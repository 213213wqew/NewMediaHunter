import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router';
import request from '../utils/request';

const routes: RouteRecordRaw[] = [
    {
        path: '/',
        redirect: '/dashboard',
    },
    {
        path: '/dashboard',
        component: () => import('../views/dashboard/index.vue'),
    },
    {
        path: '/article',
        component: () => import('../views/article/index.vue'),
    },
    {
        path: '/account',
        component: () => import('../views/account/index.vue'),
    },
    {
        path: '/channel',
        component: () => import('../views/channel/index.vue'),
    },
    {
        path: '/media',
        component: () => import('../views/media/index.vue'),
    },
    {
        path: '/hot-news',
        component: () => import('../views/hot-news/index.vue'),
    },
    {
        path: '/video',
        component: () => import('../views/video/index.vue'),
    },
    {
        path: '/task',
        component: () => import('../views/task/index.vue'),
    },
    {
        path: '/compliance',
        component: () => import('../views/compliance/index.vue'),
    },
    {
        path: '/login',
        component: () => import('../views/login/index.vue'),
    },
    {
        path: '/automation-task',
        component: () => import('../views/task/AutomationWorkspace.vue'),
    },
    {
        path: '/ai-settings',
        component: () => import('../views/ai-settings/index.vue'),
    },
    {
        path: '/ai-writing-specs',
        component: () => import('../views/ai-settings/WritingSpecIndex.vue'),
    }
];

const router = createRouter({
    history: createWebHistory(),
    routes,
});

router.beforeEach(async (to, from, next) => {
    let token = localStorage.getItem('token');
    if (!token && to.path !== '/login') {
        try {
            const restored = await request.get<{ token?: string; username?: string; role?: string }>('/auth/restore-session');
            if (restored?.token) {
                localStorage.setItem('token', restored.token);
                if (restored.username) localStorage.setItem('username', restored.username);
                if (restored.role) localStorage.setItem('role', restored.role);
                next(to.path === '/login' ? '/' : to);
                return;
            }
        } catch (_) { }
        next('/login');
        return;
    }
    if (to.path !== '/login' && !token) {
        next('/login');
    } else if (to.path === '/login' && token) {
        next('/');
    } else {
        next();
    }
});

export default router;
