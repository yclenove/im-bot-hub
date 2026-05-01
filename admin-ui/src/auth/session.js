const KEY_U = 'tg_admin_username';
const KEY_P = 'tg_admin_password';
export function setCredentials(username, password) {
    sessionStorage.setItem(KEY_U, username);
    sessionStorage.setItem(KEY_P, password);
}
export function clearCredentials() {
    sessionStorage.removeItem(KEY_U);
    sessionStorage.removeItem(KEY_P);
}
export function hasCredentials() {
    const u = sessionStorage.getItem(KEY_U);
    const p = sessionStorage.getItem(KEY_P);
    return !!u && !!p;
}
/** Basic Authorization header value, or undefined if not logged in. */
export function getBasicAuthorization() {
    const u = sessionStorage.getItem(KEY_U);
    const p = sessionStorage.getItem(KEY_P);
    if (!u || !p)
        return undefined;
    return `Basic ${btoa(`${u}:${p}`)}`;
}
