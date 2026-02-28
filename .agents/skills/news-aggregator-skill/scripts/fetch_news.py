import argparse
import json
import requests
from bs4 import BeautifulSoup
import sys
import time
import re
import concurrent.futures
from datetime import datetime

# Headers for scraping to avoid basic bot detection
HEADERS = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
}

def filter_items(items, keyword=None):
    if not keyword:
        return items
    
    # 支持逗号分隔多词; 同时对每个词，将其中的英文子串和中文词组拆分为独立词条
    def expand_keyword(k):
        k = k.strip().lower()
        if not k:
            return []
        result = [k]  # 保留原词（精确匹配优先）
        # 提取英文子串（如 "AI模型" 中的 "ai"）
        import re
        en_parts = re.findall(r'[a-z0-9]+', k)
        result.extend(p for p in en_parts if len(p) >= 2)
        # 提取中文子串（2字以上中文词组）
        zh_parts = re.findall(r'[\u4e00-\u9fff]{2,}', k)
        result.extend(zh_parts)
        return list(dict.fromkeys(result))  # 去重保序
    
    raw_keywords = [k.strip() for k in keyword.split(',') if k.strip()]
    all_keywords = []
    for kw in raw_keywords:
        all_keywords.extend(expand_keyword(kw))
    
    filtered = []
    for item in items:
        title_lower = item.get('title', '').lower()
        if any(k in title_lower for k in all_keywords):
            filtered.append(item)
            
    return filtered

def fetch_url_content(url):
    """
    智能提取网页正文。
    策略：
    1. 尝试从常见的新闻网站自带的 JSON 数据或特定结构提取 (如 36Kr 的 window.initialState)
    2. 移除干扰元素（script, style, nav, footer, ads）
    3. 优先查找 <article> 或具有 content 类名的容器
    4. 提取所有段落文字，并进行简单的字符数过滤
    """
    if not url or not url.startswith('http'):
        return ""
    try:
        # 使用更贴近浏览器的 User-Agent
        headers = dict(HEADERS)
        headers['User-Agent'] = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36'
        
        response = requests.get(url, headers=headers, timeout=8)
        response.encoding = response.apparent_encoding # 自动识别编码
        html_text = response.text
        
        # 1. 针对 36Kr 等前后端分离站点的专属优化
        if "36kr.com" in url:
            # 36kr 将正文内容放在 window.initialState 中
            import re, json
            match = re.search(r'window\.initialState\s*=\s*(\{.*?\})\s*</script>', html_text)
            if match:
                try:
                    data = json.loads(match.group(1))
                    # 尝试从复杂的 JSON 结构中定位 articleDetail
                    detail = data.get("articleDetail", {}).get("articleDetailData", {}).get("data", {})
                    widget_content = detail.get("widgetContent", "")
                    if widget_content:
                        # 提取 HTML 中的文字
                        return BeautifulSoup(widget_content, 'html.parser').get_text(separator='\n\n', strip=True)[:5000]
                except:
                    pass

        soup = BeautifulSoup(html_text, 'html.parser')
        
        # 2. 移除干扰
        for tag in soup(["script", "style", "nav", "footer", "header", "aside", "form", "noscript"]):
            tag.decompose()

        # 3. 尝试寻找核心容器
        content_container = None
        # 常见正文容器标识
        selectors = [
            'article', 
            '.article-content', '.post-content', '.content', '.main-content', '.rich_media_content',
            '#article_content', '#content', '.entry-content', '.article-detail'
        ]
        for sel in selectors:
            if sel.startswith('.'):
                content_container = soup.find(class_=sel[1:])
            elif sel.startswith('#'):
                content_container = soup.find(id=sel[1:])
            else:
                content_container = soup.find(sel)
            if content_container: break

        target = content_container if content_container else soup
        
        # 4. 提取段落文本
        paragraphs = []
        for p in target.find_all(['p', 'div', 'span']):
            # 强化过滤：避免提取仅具有少量字符且没有标点的无用 div/span
            txt = p.get_text(strip=True)
            if not txt or len(txt) < 10:
                continue
            if p.name in ['div', 'span'] and len(txt) < 30 and not any(c in txt for c in ['，', '。', '！', '？', ',', '.', '!', '?']):
                continue
            
            # 简单过滤常见“分享”、“下载”等干扰词
            if any(bad in txt for bad in ["分享到", "下载客户端", "版权所有", "点击加载更多", "扫码阅读", "相关阅读"]):
                continue
            paragraphs.append(txt)
        
        # 去重并保持顺序
        seen = set()
        unique_paragraphs = []
        for p in paragraphs:
            if p not in seen:
                unique_paragraphs.append(p)
                seen.add(p)

        # 5. 汇总
        text = '\n\n'.join(unique_paragraphs)
        if not text.strip():
            # 如果什么都没提取到，作为最后的手段，直接粗暴提取整个 body 文本
            text = soup.body.get_text(separator='\n\n', strip=True) if soup.body else soup.get_text(separator='\n\n', strip=True)
            
        return text[:5000] # 适度扩容，为 AI 提供更多上下文
    except Exception as e:
        sys.stderr.write(f"Error fetching {url}: {str(e)}\n")
        return ""

def enrich_items_with_content(items, max_workers=10):
    with concurrent.futures.ThreadPoolExecutor(max_workers=max_workers) as executor:
        future_to_item = {executor.submit(fetch_url_content, item['url']): item for item in items}
        for future in concurrent.futures.as_completed(future_to_item):
            item = future_to_item[future]
            try:
                content = future.result()
                if content:
                    item['content'] = content
            except Exception:
                item['content'] = ""
    return items

# --- Source Fetchers ---

def fetch_hackernews(limit=5, keyword=None):
    if keyword:
        # Use Algolia API for keyword search (Much better recall for specific topics like "AI")
        try:
            # 24h window
            timestamp_24h = int(time.time() - 24 * 3600)
            
            # Query builder strategy
            raw_keywords = [k.strip() for k in keyword.split(',')]
            
            # 1. Try Complex Query with Quoted Phrases
            # "Github Copilot" needs quotes in Algolia search string if mixed with OR
            quoted_keywords = [f'"{k}"' if ' ' in k else k for k in raw_keywords]
            query_str = " OR ".join(quoted_keywords)
            
            api_url = f"http://hn.algolia.com/api/v1/search_by_date?tags=story&numericFilters=created_at_i>{timestamp_24h}&hitsPerPage=100&query={requests.utils.quote(query_str)}"
            
            data = requests.get(api_url, timeout=4).json()
            hits = data.get('hits', [])
            
            # 2. Level 2 Fallback: If 0 results, try just the first keyword (usually the most broad, e.g. "AI")
            if not hits and raw_keywords:
                simple_query = raw_keywords[0]
                api_url_simple = f"http://hn.algolia.com/api/v1/search_by_date?tags=story&numericFilters=created_at_i>{timestamp_24h}&hitsPerPage=100&query={requests.utils.quote(simple_query)}"
                data = requests.get(api_url_simple, timeout=4).json()
                hits = data.get('hits', [])

            items = []
            for hit in hits:
                items.append({
                    "source": "Hacker News",
                    "title": hit.get('title'),
                    "url": hit.get('url') or f"https://news.ycombinator.com/item?id={hit['objectID']}",
                    "hn_url": f"https://news.ycombinator.com/item?id={hit['objectID']}",
                    "heat": f"{hit.get('points', 0)} points",
                    "time": "Today" # Algolia return is recent by definition of filter
                })
            
            # Only return if we actually found something. 
            # If we found nothing after all attempts, we might want to fall back to scraping frontpage 
            # but frontpage is unlikely to have keyword matches if deep search failed. 
            # However, returning [] is better than hallucinating.
            return items[:limit]
            
        except Exception as e:
            print(f"HN Algolia failed: {e}", file=sys.stderr)
            # Fallback to scraping logic below if API completely errors out (e.g. network/timeout)
            pass

    # Fallback / Default: Scrape Front Page
    base_url = "https://news.ycombinator.com"
    news_items = []
    page = 1
    max_pages = 10
    
    while len(news_items) < limit and page <= max_pages:
        url = f"{base_url}/news?p={page}"
        try:
            response = requests.get(url, headers=HEADERS, timeout=4)
            if response.status_code != 200: break
        except: break

        soup = BeautifulSoup(response.text, 'html.parser')
        rows = soup.select('.athing')
        if not rows: break
        
        page_items = []
        for row in rows:
            try:
                id_ = row.get('id')
                title_line = row.select_one('.titleline a')
                if not title_line: continue
                title = title_line.get_text()
                link = title_line.get('href')
                
                # Metadata
                score_span = soup.select_one(f'#score_{id_}')
                score = score_span.get_text() if score_span else "0 points"
                
                # Age/Time
                age_span = soup.select_one(f'.age a[href="item?id={id_}"]')
                time_str = age_span.get_text() if age_span else ""
                
                if link and link.startswith('item?id='): link = f"{base_url}/{link}"
                
                page_items.append({
                    "source": "Hacker News", 
                    "title": title, 
                    "url": link, 
                    "hn_url": f"{base_url}/item?id={id_}",
                    "heat": score,
                    "time": time_str
                })
            except: continue
        
        news_items.extend(filter_items(page_items, keyword))
        if len(news_items) >= limit: break
        page += 1

    return news_items[:limit]

def fetch_weibo(limit=5, keyword=None):
    # Use the PC Ajax API which returns JSON directly and is less rate-limited than scraping s.weibo.com
    url = "https://weibo.com/ajax/side/hotSearch"
    headers = {
        "User-Agent": "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Referer": "https://weibo.com/"
    }
    
    try:
        response = requests.get(url, headers=headers, timeout=4)
        data = response.json()
        items = data.get('data', {}).get('realtime', [])
        
        all_items = []
        for item in items:
            # key 'note' is usually the title, sometimes 'word'
            title = item.get('note', '') or item.get('word', '')
            if not title: continue
            
            # 'num' is the heat value
            heat = item.get('num', 0)
            
            # Construct URL (usually search query)
            # Web UI uses: https://s.weibo.com/weibo?q=%23TITLE%23&Refer=top
            full_url = f"https://s.weibo.com/weibo?q={requests.utils.quote(title)}&Refer=top"
            
            all_items.append({
                "source": "Weibo Hot Search", 
                "title": title, 
                "url": full_url, 
                "heat": f"{heat}",
                "time": "Real-time"
            })
            
        return filter_items(all_items, keyword)[:limit]
    except Exception: 
        return []

def fetch_github(limit=5, keyword=None):
    if keyword:
         # Use GitHub Search for keywords
         query = f"{keyword.split(',')[0]} sort:updated" # Use first kw as primary
         url = f"https://github.com/search?q={requests.utils.quote(query)}&type=repositories"
         # Note: GitHub Search page is hard to scrape due to login requirements (often).
         # Fallback strat: Topics? "https://github.com/topics/{kw}?o=desc&s=updated"
         topic_url = f"https://github.com/topics/{keyword.split(',')[0].strip()}?o=desc&s=updated"
         try:
             response = requests.get(topic_url, headers=HEADERS, timeout=4)
             if response.status_code == 200:
                soup = BeautifulSoup(response.text, 'html.parser')
                items = []
                for article in soup.select('article.border'):
                     # Topic page structure changes often, but let's try generic selector
                     h3 = article.select_one('h3 a') 
                     # Actually standard topic page: <h3 class="f3"><a href="/user/repo">...
                     if not h3: continue
                     repo_link = h3['href'] # /user/repo
                     title = repo_link.strip('/')
                     link = "https://github.com" + repo_link
                     
                     desc = ""
                     desc_div = article.select_one('.color-fg-muted')
                     if desc_div: desc = desc_div.get_text(strip=True)
                     
                     items.append({
                        "source": "GitHub Trending", 
                        "title": f"{title} - {desc}", 
                        "url": link,
                        "heat": "Topic Match",
                        "time": "Updated recently"
                     })
                if items: return items[:limit]
         except: pass

    # Default Trending
    try:
        response = requests.get("https://github.com/trending", headers=HEADERS, timeout=4)
    except: return []
    
    soup = BeautifulSoup(response.text, 'html.parser')
    items = []
    for article in soup.select('article.Box-row'):
        try:
            h2 = article.select_one('h2 a')
            if not h2: continue
            title = h2.get_text(strip=True).replace('\n', '').replace(' ', '')
            link = "https://github.com" + h2['href']
            
            desc = article.select_one('p')
            desc_text = desc.get_text(strip=True) if desc else ""
            
            # Stars (Heat)
            # usually the first 'Link--muted' with a SVG star
            stars_tag = article.select_one('a[href$="/stargazers"]')
            stars = stars_tag.get_text(strip=True) if stars_tag else ""
            
            items.append({
                "source": "GitHub Trending", 
                "title": f"{title} - {desc_text}", 
                "url": link,
                "heat": f"{stars} stars",
                "time": "Today"
            })
        except: continue
    return filter_items(items, keyword)[:limit]

def fetch_36kr(limit=5, keyword=None):
    try:
        response = requests.get("https://36kr.com/newsflashes", headers=HEADERS, timeout=4)
        soup = BeautifulSoup(response.text, 'html.parser')
        items = []
        for item in soup.select('.newsflash-item'):
            title = item.select_one('.item-title').get_text(strip=True)
            href = item.select_one('.item-title')['href']
            time_tag = item.select_one('.time')
            time_str = time_tag.get_text(strip=True) if time_tag else ""
            
            items.append({
                "source": "36Kr", 
                "title": title, 
                "url": f"https://36kr.com{href}" if not href.startswith('http') else href,
                "time": time_str,
                "heat": ""
            })
        return filter_items(items, keyword)[:limit]
    except: return []

def fetch_v2ex(limit=5, keyword=None):
    try:
        # Hot topics json
        data = requests.get("https://www.v2ex.com/api/topics/hot.json", headers=HEADERS, timeout=4).json()
        items = []
        for t in data:
            # V2EX API fields: created, replies (heat)
            replies = t.get('replies', 0)
            created = t.get('created', 0)
            # convert epoch to readable if possible, simpler to just leave as is or basic format
            # Let's keep it simple
            items.append({
                "source": "V2EX", 
                "title": t['title'], 
                "url": t['url'],
                "heat": f"{replies} replies",
                "time": "Hot"
            })
        return filter_items(items, keyword)[:limit]
    except: return []

def fetch_tencent(limit=5, keyword=None):
    try:
        url = "https://i.news.qq.com/web_backend/v2/getTagInfo?tagId=aEWqxLtdgmQ%3D"
        data = requests.get(url, headers={"Referer": "https://news.qq.com/"}, timeout=4).json()
        items = []
        for news in data['data']['tabs'][0]['articleList']:
            items.append({
                "source": "Tencent News", 
                "title": news['title'], 
                "url": news.get('url') or news.get('link_info', {}).get('url'),
                "time": news.get('pub_time', '') or news.get('publish_time', '')
            })
        return filter_items(items, keyword)[:limit]
    except: return []

def fetch_wallstreetcn(limit=5, keyword=None):
    try:
        url = "https://api-one.wallstcn.com/apiv1/content/information-flow?channel=global-channel&accept=article&limit=100"
        data = requests.get(url, timeout=4).json()
        items = []
        for item in data['data']['items']:
            res = item.get('resource')
            if res and (res.get('title') or res.get('content_short')):
                 ts = res.get('display_time', 0)
                 time_str = datetime.fromtimestamp(ts).strftime('%Y-%m-%d %H:%M') if ts else ""
                 items.append({
                     "source": "Wall Street CN", 
                     "title": res.get('title') or res.get('content_short'), 
                     "url": res.get('uri'),
                     "time": time_str
                 })
        return filter_items(items, keyword)[:limit]
    except: return []

def fetch_producthunt(limit=5, keyword=None):
    try:
        # Using RSS for speed and reliability without API key
        response = requests.get("https://www.producthunt.com/feed", headers=HEADERS, timeout=4)
        soup = BeautifulSoup(response.text, 'html.parser')
        
        items = []
        for entry in soup.find_all(['item', 'entry']):
            title = entry.find('title').get_text(strip=True)
            link_tag = entry.find('link')
            url = link_tag.get('href') or link_tag.get_text(strip=True) if link_tag else ""
            
            pubBox = entry.find('pubDate') or entry.find('published')
            pub = pubBox.get_text(strip=True) if pubBox else ""
            
            items.append({
                "source": "Product Hunt", 
                "title": title, 
                "url": url,
                "time": pub,
                "heat": "Top Product" # RSS implies top rank
            })
        return filter_items(items, keyword)[:limit]
    except: return []

def fetch_sina(limit=5, keyword=None):
    """新浪新闻 -- 调用新浪实时滚动新闻 API"""
    try:
        url = f"https://feed.mix.sina.com.cn/api/roll/get?pageid=153&lid=2509&k=&num=100&page=1"
        response = requests.get(url, headers=HEADERS, timeout=4)
        data = response.json()
        items = []
        for art in data.get('result', {}).get('data', []):
            title = art.get('title', '')
            if not title: continue
            
            # 时间戳转可视化时间
            try:
                import datetime
                ctime = datetime.datetime.fromtimestamp(int(art.get('ctime'))).strftime('%Y-%m-%d %H:%M:%S')
            except:
                ctime = ""
                
            items.append({
                "source": "新浪新闻",
                "title": title,
                "url": art.get('url', ''),
                "time": ctime,
                "heat": ""
            })
        return filter_items(items, keyword)[:limit]
    except: return []

def fetch_netease(limit=5, keyword=None):
    """网易新闻 -- 调用网易移动端热点 API"""
    try:
        url = "https://m.163.com/fe/api/hot/news/flow?size=100"
        headers = dict(HEADERS)
        headers["Referer"] = "https://m.163.com/"
        response = requests.get(url, headers=headers, timeout=4)
        data = response.json()
        items = []
        for art in data.get('data', {}).get('list', []):
            title = art.get('title', '')
            if not title: continue
            
            # 使用返回的 url 或者 docid 拼接
            link = art.get('url')
            if not link and art.get('docid'):
                link = f"https://m.163.com/news/article/{art['docid']}.html"
                
            items.append({
                "source": "网易新闻",
                "title": title,
                "url": link or "",
                "time": art.get('ptime', ''),
                "heat": str(art.get('replyCount', '')) if art.get('replyCount') else ""
            })
        return filter_items(items, keyword)[:limit]
    except: return []

def fetch_sohu(limit=5, keyword=None):
    """搜狐新闻 -- 调用搜狐新闻推荐 API"""
    try:
        url = "https://v2.sohu.com/picfeed?feedType=7&page=0&size=100&disableRecommend=0"
        headers = dict(HEADERS)
        headers["Referer"] = "https://news.sohu.com/"
        response = requests.get(url, headers=headers, timeout=4)
        data = response.json()
        items = []
        feeds = data.get('feeds') or data.get('data', {}).get('feeds', [])
        for item in (feeds or []):
            art = item.get('src') or item
            title = art.get('title', '')
            if not title: continue
            link = art.get('link') or art.get('url', '')
            items.append({
                "source": "搜狐新闻",
                "title": title,
                "url": link,
                "time": art.get('pubTime', ''),
                "heat": str(art.get('readNum', '') or '')
            })
        return filter_items(items, keyword)[:limit]
    except: return []

def fetch_cctv(limit=5, keyword=None):
    """央视网新闻 -- 使用央视网 RSS (society已废除, 换要闻)"""
    try:
        url = "https://news.cctv.com/2019/07/gaqing/jianjie/jiekou/yaowen.xml"
        response = requests.get(url, headers=HEADERS, timeout=4)
        response.encoding = 'utf-8'
        soup = BeautifulSoup(response.content, 'xml')
        items = []
        for entry in soup.find_all('item'):
            title_tag = entry.find('title')
            link_tag = entry.find('link')
            pub_tag = entry.find('pubDate')
            if not title_tag: continue
            items.append({
                "source": "央视网",
                "title": title_tag.get_text(strip=True),
                "url": link_tag.get_text(strip=True) if link_tag else "",
                "time": pub_tag.get_text(strip=True) if pub_tag else "",
                "heat": ""
            })
        return filter_items(items, keyword)[:limit]
    except: return []

def fetch_toutiao(limit=5, keyword=None):
    """今日头条 -- 调用头条热榜 API"""
    try:
        headers = dict(HEADERS)
        headers["Referer"] = "https://www.toutiao.com/"
        url = "https://www.toutiao.com/hot-event/hot-board/?origin=toutiao_pc"
        response = requests.get(url, headers=headers, timeout=5)
        data = response.json()
        items = []
        for art in data.get("data", []):
            title = art.get("Title", "")
            if not title: continue
            link = art.get("Url", "")
            hot = art.get("HotValue", "")
            items.append({
                "source": "今日头条",
                "title": title,
                "url": link,
                "time": "",
                "heat": str(hot) if hot else ""
            })
        return filter_items(items, keyword)[:limit]
    except: return []

def fetch_baidu(limit=5, keyword=None):
    """百度热搜 -- 从 top.baidu.com 提取实时热榜"""
    try:
        url = "https://top.baidu.com/board?tab=realtime"
        response = requests.get(url, headers=HEADERS, timeout=5)
        soup = BeautifulSoup(response.text, 'html.parser')
        results = []
        for a in soup.find_all('a', href=True):
            if 'wd=' in a['href'] and 'sa=fyb_news' in a['href']:
                title = a.get_text(strip=True)
                if len(title) > 2:
                    results.append({
                        "source": "百度热搜",
                        "title": title,
                        "url": a['href'] if a['href'].startswith('http') else f"https://www.baidu.com{a['href']}",
                        "time": "",
                        "heat": ""
                    })
        
        # 去重
        seen = set()
        unique_results = []
        for res in results:
            if res['title'] not in seen:
                unique_results.append(res)
                seen.add(res['title'])
                
        return filter_items(unique_results, keyword)[:limit]
    except: return []

def fetch_bilibili(limit=5, keyword=None):
    """Bilibili 热搜 -- 调用 B 站搜索广场 API"""
    try:
        url = "https://api.bilibili.com/x/web-interface/search/square?limit=50"
        response = requests.get(url, headers=HEADERS, timeout=5)
        data = response.json()
        trending = data.get('data', {}).get('trending', {})
        items = []
        for entry in trending.get('list', []):
            title = entry.get('keyword', '')
            if not title: continue
            items.append({
                "source": "Bilibili",
                "title": title,
                "url": f"https://search.bilibili.com/all?keyword={requests.utils.quote(title)}",
                "time": "",
                "heat": ""
            })
        return filter_items(items, keyword)[:limit]
    except: return []

def main():
    parser = argparse.ArgumentParser()
    sources_map = {
        'hackernews': fetch_hackernews, 'weibo': fetch_weibo, 'github': fetch_github,
        '36kr': fetch_36kr, 'v2ex': fetch_v2ex, 'tencent': fetch_tencent,
        'wallstreetcn': fetch_wallstreetcn, 'producthunt': fetch_producthunt,
        'sina': fetch_sina, 'netease': fetch_netease, 'sohu': fetch_sohu, 'cctv': fetch_cctv,
        'toutiao': fetch_toutiao, 'baidu': fetch_baidu, 'bilibili': fetch_bilibili
    }
    
    parser.add_argument('--source', help='Source(s) to fetch from (comma-separated)')
    parser.add_argument('--limit', type=int, default=10, help='Limit per source. Default 10')
    parser.add_argument('--keyword', help='Comma-sep keyword filter')
    parser.add_argument('--deep', action='store_true', help='Download article content for detailed summarization')
    parser.add_argument('--url', help='Fetch content for a single specific URL')
    
    args = parser.parse_args()

    # Mode 1: Single URL Fetch
    if args.url:
        content = fetch_url_content(args.url)
        print(json.dumps({"url": args.url, "content": content}, ensure_ascii=False))
        return

    # Mode 2: Multi-Source Heat Search
    if not args.source:
        args.source = 'all'
    
    to_run = []
    if args.source == 'all':
        to_run = list(sources_map.values())
    else:
        requested_sources = [s.strip() for s in args.source.split(',')]
        for s in requested_sources:
            if s in sources_map: to_run.append(sources_map[s])
            
    results = []
    
    def run_fetchers(fetchers, limit, kw):
        res = []
        if not fetchers:
            return res
        
        # 使用线程池并发抓取各大平台，受网络 I/O 影响，多线程可将时间从累加变为取最长
        with concurrent.futures.ThreadPoolExecutor(max_workers=len(fetchers)) as executor:
            future_to_func = {executor.submit(func, limit, kw): func for func in fetchers}
            for future in concurrent.futures.as_completed(future_to_func):
                try:
                    data = future.result()
                    if data:
                        res.extend(data)
                except Exception:
                    pass
        return res

    # Primary Fetch
    results = run_fetchers(to_run, args.limit, args.keyword)

    if args.deep and results:
        sys.stderr.write(f"Deep fetching content for {len(results)} items...\n")
        results = enrich_items_with_content(results)
        
    print(json.dumps(results, indent=2, ensure_ascii=False))

if __name__ == "__main__":
    main()
