/**
 * Per-section load results.
 *
 * A page section is either loaded or explicitly unavailable. Nothing in this
 * module ever substitutes a zero, an empty list, or any other plausible-looking
 * value for data that failed to load: "0.00" and "no records" are statements
 * about the User's money, and a loader that cannot answer must say so.
 */

/** Why a section could not be loaded. */
export type SectionFailure =
	/** The request never completed — the backend was unreachable. */
	| 'unreachable'
	/** The backend answered with an error status. */
	| 'error'
	/** The backend answered successfully with a body we cannot trust. */
	| 'invalid';

export type Section<T> = { status: 'ok'; data: T } | { status: 'unavailable'; reason: SectionFailure };

export function sectionOk<T>(data: T): Section<T> {
	return { status: 'ok', data };
}

export function sectionUnavailable<T>(reason: SectionFailure): Section<T> {
	return { status: 'unavailable', reason };
}

/** The section's data, or `null` when it is unavailable. Never a stand-in value. */
export function sectionValue<T>(section: Section<T>): T | null {
	return section.status === 'ok' ? section.data : null;
}

export function isUnavailable<T>(section: Section<T>): boolean {
	return section.status === 'unavailable';
}

/** True when at least one of the given sections is unavailable. */
export function anyUnavailable(...sections: Array<Section<unknown>>): boolean {
	return sections.some((section) => section.status === 'unavailable');
}
