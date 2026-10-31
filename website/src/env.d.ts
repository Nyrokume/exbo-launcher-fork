/**
 * Starlight publishes `virtual:starlight/user-config` from `virtual.d.ts`.
 * `virtual:starlight/user-images` stays in `virtual-internal.d.ts`, which a
 * user component override is not compiled against. Declare the same shape so
 * `astro check` can see the logos `SiteTitle.astro` renders.
 */
declare module 'virtual:starlight/user-images' {
	type ImageMetadata = import('astro').ImageMetadata;
	export const logos: {
		dark?: ImageMetadata;
		light?: ImageMetadata;
	};
}
