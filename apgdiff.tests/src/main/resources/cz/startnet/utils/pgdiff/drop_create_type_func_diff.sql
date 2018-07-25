SET search_path = pg_catalog;

-- DEPCY: This FUNCTION depends on the TYPE: public.typ_range

DROP FUNCTION public.add(typ_range, integer);

DROP TYPE public.typ_range;

-- DEPCY: This TYPE is a dependency of FUNCTION: public.add(typ_range, integer)

CREATE TYPE public.typ_range AS RANGE (
	subtype = character varying,
	collation = pg_catalog."ru_RU"
);

ALTER TYPE public.typ_range OWNER TO botov_av;

CREATE OR REPLACE FUNCTION public.add(typ_range, integer) RETURNS integer
    LANGUAGE sql IMMUTABLE STRICT
    AS $_$select $2;$_$;

ALTER FUNCTION public.add(typ_range, integer) OWNER TO botov_av;
