export declare namespace Schema {
	export interface Schema<Type = unknown, Encoded = Type> {
		readonly Type?: Type;
		readonly Encoded?: Encoded;
	}

	export type Type<T> = T extends Schema<infer TypeValue, unknown> ? TypeValue : unknown;
	export type Encoded<T> = T extends Schema<unknown, infer EncodedValue> ? EncodedValue : unknown;
}

export declare const Schema: unknown;
