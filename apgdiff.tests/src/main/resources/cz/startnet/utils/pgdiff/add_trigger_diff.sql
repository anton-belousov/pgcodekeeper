SET search_path = pg_catalog;

CREATE OR REPLACE FUNCTION public.test_table_trigger() RETURNS "trigger"
    AS $$
begin
	return NEW;
end;
$$
    LANGUAGE plpgsql;

ALTER FUNCTION public.test_table_trigger() OWNER TO fordfrog;

CREATE TRIGGER test_table_trigger
	BEFORE INSERT OR UPDATE ON public.test_table
	FOR EACH ROW
	EXECUTE PROCEDURE public.test_table_trigger();

CREATE TRIGGER test_view_trigger1
	INSTEAD OF INSERT OR UPDATE ON public.test_view
	FOR EACH ROW
	EXECUTE PROCEDURE public.test_table_trigger();

CREATE TRIGGER test_view_trigger2
	AFTER INSERT OR UPDATE ON public.test_view
	FOR EACH STATEMENT
	EXECUTE PROCEDURE public.test_table_trigger();
