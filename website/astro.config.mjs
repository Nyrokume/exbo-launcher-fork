// @ts-check
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { defineConfig } from 'astro/config';
import starlight from '@astrojs/starlight';

/**
 * Dev-only. Production builds do not copy docs/screenshots (EXBO assets stay off GitHub Pages).
 * @returns {import('vite').Plugin}
 */
function zoneShotsDev() {
	const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../docs/screenshots');
	return {
		name: 'zone-shots-dev',
		configureServer(server) {
			server.middlewares.use((req, res, next) => {
				const url = req.url?.split('?')[0] ?? '';
				const marker = '/__zone-shots/';
				const at = url.indexOf(marker);
				if (at < 0) return next();
				const rel = decodeURIComponent(url.slice(at + marker.length));
				if (!rel || rel.includes('..') || rel.includes('\\')) {
					res.statusCode = 400;
					res.end();
					return;
				}
				const file = path.resolve(root, rel);
				const base = path.resolve(root) + path.sep;
				if (!file.startsWith(base) || !fs.existsSync(file) || !fs.statSync(file).isFile()) {
					res.statusCode = 404;
					res.end();
					return;
				}
				res.setHeader('Content-Type', 'image/png');
				fs.createReadStream(file).pipe(res);
			});
		},
	};
}

const fonts =
	'https://fonts.googleapis.com/css2?family=Bebas+Neue&family=IBM+Plex+Mono:wght@400;500&family=Manrope:wght@400;500;600&display=swap';

// https://astro.build/config
export default defineConfig({
	site: 'https://nyrokume.github.io',
	base: '/exbo-launcher-fork',
	vite: { plugins: [zoneShotsDev()] },
	integrations: [
		starlight({
			title: 'EXBO Docs',
			description: 'Окна, темы и настройки визуального форка лаунчера EXBO.',
			favicon: '/favicon.svg',
			defaultLocale: 'root',
			locales: {
				root: {
					label: 'Русский',
					lang: 'ru',
				},
				en: {
					label: 'English',
					lang: 'en',
				},
			},
			social: [
				{
					icon: 'github',
					label: 'GitHub',
					href: 'https://github.com/Nyrokume/exbo-launcher-fork',
				},
			],
			editLink: {
				baseUrl: 'https://github.com/Nyrokume/exbo-launcher-fork/edit/main/website/',
			},
			lastUpdated: true,
			customCss: ['./src/styles/custom.css'],
			head: [
				{
					tag: 'link',
					attrs: { rel: 'preconnect', href: 'https://fonts.googleapis.com' },
				},
				{
					tag: 'link',
					attrs: {
						rel: 'preconnect',
						href: 'https://fonts.gstatic.com',
						crossorigin: 'anonymous',
					},
				},
				{
					tag: 'link',
					attrs: { rel: 'stylesheet', href: fonts },
				},
			],
			sidebar: [
				{
					label: 'Старт',
					translations: { en: 'Start' },
					collapsed: true,
					items: [
						{ slug: 'getting-started/overview', label: 'Обзор', translations: { en: 'Overview' } },
						{ slug: 'getting-started/install-run', label: 'Установка', translations: { en: 'Install' } },
						{ slug: 'getting-started/mock-login', label: 'Mock/live вход', translations: { en: 'Mock/live login' } },
						{ slug: 'getting-started/two-factor', label: '2FA (TOTP/mail)', translations: { en: '2FA (TOTP/mail)' } },
						{ slug: 'getting-started/update', label: 'Update fail-closed', translations: { en: 'Update fail-closed' } },
						{ slug: 'getting-started/themes', label: 'Темы', translations: { en: 'Themes' } },
					],
				},
				{
					label: 'Игра',
					translations: { en: 'Game' },
					collapsed: true,
					items: [
						{ slug: 'game/home', label: 'Дом', translations: { en: 'Home' } },
						{ slug: 'game/servers', label: 'Серверы', translations: { en: 'Servers' } },
						{ slug: 'game/launch', label: 'Запуск', translations: { en: 'Launch' } },
						{ slug: 'game/accounts', label: 'Аккаунты', translations: { en: 'Accounts' } },
					],
				},
				{
					label: 'Сеть',
					translations: { en: 'Network' },
					collapsed: true,
					items: [
						{ slug: 'settings/tunnel', label: 'Туннель layout', translations: { en: 'Tunnel layout' } },
						{ slug: 'network/states', label: 'Loading/error/empty', translations: { en: 'Loading/error/empty' } },
						{ slug: 'network/mock-catalog', label: 'Mock classpath', translations: { en: 'Mock classpath' } },
						{ slug: 'network/p2p', label: 'P2P позже', translations: { en: 'P2P later' } },
					],
				},
				{
					label: 'Настройки',
					translations: { en: 'Settings' },
					collapsed: true,
					items: [
						{ slug: 'settings/behavior', label: 'Поведение', translations: { en: 'Behavior' } },
						{ slug: 'settings/jvm', label: 'JVM', translations: { en: 'JVM' } },
						{ slug: 'settings/sensitivity', label: 'Чувствительность', translations: { en: 'Sensitivity' } },
						{ slug: 'settings/accounts', label: 'Аккаунты', translations: { en: 'Accounts' } },
					],
				},
				{
					label: 'Архитектура',
					translations: { en: 'Architecture' },
					collapsed: true,
					items: [
						{ slug: 'architecture/windows', label: 'Окна и размеры', translations: { en: 'Windows and sizes' } },
						{ slug: 'architecture/shell', label: 'Shell scale', translations: { en: 'Shell scale' } },
						{ slug: 'architecture/app-context', label: 'AppContext', translations: { en: 'AppContext' } },
						{ slug: 'architecture/navigation', label: 'Навигация', translations: { en: 'Navigation' } },
					],
				},
				{
					label: 'Дизайн',
					translations: { en: 'Design' },
					collapsed: true,
					items: [
						{ slug: 'design/style-zone', label: 'Zone', translations: { en: 'Zone' } },
						{ slug: 'design/interaction', label: 'Interaction', translations: { en: 'Interaction' } },
					],
				},
				{
					label: 'Справка',
					translations: { en: 'Reference' },
					collapsed: true,
					items: [
						{ slug: 'reference/gradle-flags', label: 'Gradle-флаги', translations: { en: 'Gradle flags' } },
						{ slug: 'reference/changelog', label: 'Changelog', translations: { en: 'Changelog' } },
						{ slug: 'reference/screenshots', label: 'Скриншоты', translations: { en: 'Screenshots' } },
						{ slug: 'reference/limitations', label: 'Ограничения', translations: { en: 'Limitations' } },
					],
				},
			],
		}),
	],
});
