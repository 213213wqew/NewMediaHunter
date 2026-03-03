import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router';

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
    }
];

const router = createRouter({
    history: createWebHistory(),
    routes,
});

router.beforeEach((to, from, next) => {
    const token = localStorage.getItem('token');
    if (to.path !== '/login' && !token) {
        next('/login');
    } else if (to.path === '/login' && token) {
        next('/');
    } else {
        next();
    }
});

export default router;
