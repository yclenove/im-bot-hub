import { createRouter, createWebHistory } from 'vue-router';
import Dashboard from '../views/Dashboard.vue';
import { hasCredentials } from '../auth/session';
const router = createRouter({
    history: createWebHistory(import.meta.env.BASE_URL),
    routes: [
        { path: '/login', name: 'login', component: () => import('../views/Login.vue') },
        { path: '/', name: 'home', component: Dashboard },
        {
            path: '/guide',
            name: 'guide',
            component: () => import('../views/UserGuide.vue'),
        },
    ],
});
router.beforeEach((to) => {
    if (to.path === '/login') {
        if (hasCredentials()) {
            return { path: '/' };
        }
        return true;
    }
    if (!hasCredentials()) {
        return { path: '/login' };
    }
    return true;
});
export default router;
