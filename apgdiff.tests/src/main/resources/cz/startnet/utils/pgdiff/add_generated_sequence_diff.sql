SET search_path = pg_catalog;

ALTER TABLE public.testtable
	ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
	SEQUENCE NAME public.custom_named_seq
	START WITH 1
	INCREMENT BY 2
	NO MAXVALUE
	NO MINVALUE
	CACHE 1
);
