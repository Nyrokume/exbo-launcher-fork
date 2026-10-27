// @ts-check
import { defineConfig } from 'astro/config';
import starlight from '@astrojs/starlight';

const fonts =
	'https://fonts.googleapis.com/css2?family=Bebas+Neue&family=IBM+Plex+Mono:wght@400;500&family=Manrope:wght@400;500;600&display=swap';

// https://astro.build/config
export default defineConfig({
	site: 'https://nyrokume.github.io',
	base: '/exbo-launcher-fork',
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
				{ slug: 'docs' },
				{
					label: 'Начало работы',
					translations: { en: 'Getting started' },
					collapsed: true,
					items: [
						{ slug: 'getting-started/overview' },
						{ slug: 'getting-started/install-run' },
						{ slug: 'getting-started/mock-login' },
						{ slug: 'getting-started/themes' },
					],
				},
				{
					label: 'Архитектура',
					translations: { en: 'Architecture' },
					collapsed: true,
					items: [
						{ slug: 'architecture/windows' },
						{ slug: 'architecture/app-context' },
						{ slug: 'architecture/navigation' },
					],
				},
				{
					label: 'Дизайн',
					translations: { en: 'Design' },
					collapsed: true,
					items: [
						{ slug: 'design/style-zone' },
						{ slug: 'design/interaction' },
					],
				},
				{
					label: 'Настройки',
					translations: { en: 'Settings' },
					collapsed: true,
					items: [
						{ slug: 'settings/tunnel' },
						{ slug: 'settings/jvm' },
						{ slug: 'settings/accounts' },
					],
				},
				{
					label: 'Справочник',
					translations: { en: 'Reference' },
					collapsed: true,
					items: [
						{ slug: 'reference/gradle-flags' },
						{ slug: 'reference/changelog' },
					],
				},
			],
		}),
	],
});
