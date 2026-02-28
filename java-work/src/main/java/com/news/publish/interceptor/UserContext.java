package com.news.publish.interceptor;

import com.news.publish.model.entity.SysUser;

public class UserContext {
    private static final ThreadLocal<SysUser> userThreadLocal = new ThreadLocal<>();

    public static void setUser(SysUser user) {
        userThreadLocal.set(user);
    }

    public static SysUser getUser() {
        return userThreadLocal.get();
    }

    public static Long getUserId() {
        SysUser user = getUser();
        return user != null ? user.getId() : null;
    }
    
    public static boolean isAdmin() {
        SysUser user = getUser();
        return user != null && "ADMIN".equals(user.getRole());
    }

    public static void clear() {
        userThreadLocal.remove();
    }
}
