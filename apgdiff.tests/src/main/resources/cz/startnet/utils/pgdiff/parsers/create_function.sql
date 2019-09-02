CREATE FUNCTION functest_A_1(text, date) RETURNS bool LANGUAGE sql AS 'SELECT $1 = ''abcd'' AND $2 > ''2001-01-01''';
CREATE FUNCTION functest_A_2(text[]) RETURNS int LANGUAGE sql AS 'SELECT $1[0]::int';
CREATE FUNCTION functest_A_3() RETURNS bool LANGUAGE sql AS 'SELECT false';
CREATE FUNCTION functest_B_1(int) RETURNS bool LANGUAGE sql AS 'SELECT $1 > 0';
CREATE FUNCTION functest_B_2(int) RETURNS bool LANGUAGE sql IMMUTABLE AS 'SELECT $1 > 0';
CREATE FUNCTION functest_B_3(int) RETURNS bool LANGUAGE sql STABLE AS 'SELECT $1 = 0';
CREATE FUNCTION functest_B_4(int) RETURNS bool LANGUAGE sql VOLATILE AS 'SELECT $1 < 0';
ALTER FUNCTION functest_B_2(int) VOLATILE;
ALTER FUNCTION functest_B_3(int) COST 100;  -- unrelated change, no effect
CREATE FUNCTION functest_C_1(int) RETURNS bool LANGUAGE sql AS 'SELECT $1 > 0';
CREATE FUNCTION functest_C_2(int) RETURNS bool LANGUAGE sql SECURITY DEFINER AS 'SELECT $1 = 0';
CREATE FUNCTION functest_C_3(int) RETURNS bool LANGUAGE sql SECURITY INVOKER AS 'SELECT $1 < 0';
ALTER FUNCTION functest_C_1(int) IMMUTABLE; -- unrelated change, no effect
ALTER FUNCTION functest_C_2(int) SECURITY INVOKER;
ALTER FUNCTION functest_C_3(int) SECURITY DEFINER;
CREATE FUNCTION functest_E_2(int) RETURNS bool LANGUAGE sql LEAKPROOF AS 'SELECT $1 > 100';
ALTER FUNCTION functest_E_1(int) LEAKPROOF;
ALTER FUNCTION functest_E_2(int) STABLE;    -- unrelated change, no effect
ALTER FUNCTION functest_E_2(int) NOT LEAKPROOF; -- remove leakproof attribute
ALTER FUNCTION functest_E_2(int) OWNER TO regress_unpriv_user;
ALTER FUNCTION functest_E_1(int) NOT LEAKPROOF;
ALTER FUNCTION functest_E_2(int) LEAKPROOF;
CREATE FUNCTION functest_F_1(int) RETURNS bool LANGUAGE sql AS 'SELECT $1 > 50';
CREATE FUNCTION functest_F_2(int) RETURNS bool LANGUAGE sql CALLED ON NULL INPUT AS 'SELECT $1 = 50';
CREATE FUNCTION functest_F_3(int) RETURNS bool LANGUAGE sql RETURNS NULL ON NULL INPUT AS 'SELECT $1 < 50';
CREATE FUNCTION functest_F_4(int) RETURNS bool LANGUAGE sql STRICT AS 'SELECT $1 = 50';
ALTER FUNCTION functest_F_1(int) IMMUTABLE; -- unrelated change, no effect
ALTER FUNCTION functest_F_2(int) STRICT;
ALTER FUNCTION functest_F_3(int) CALLED ON NULL INPUT;
CREATE FUNCTION functest_IS_1(a int, b int default 1, c text default 'foo') RETURNS int LANGUAGE SQL AS 'SELECT $1 + $2';
CREATE FUNCTION functest_IS_2(out a int, b int default 1) RETURNS int LANGUAGE SQL AS 'SELECT $1';
CREATE FUNCTION functest_IS_3(a int default 1, out b int) RETURNS int LANGUAGE SQL AS 'SELECT $1';
DROP FUNCTION functest_IS_1(int, int, text);
CREATE FUNCTION functest_B_2(bigint) RETURNS bool LANGUAGE sql IMMUTABLE AS 'SELECT $1 > 0';
DROP FUNCTION functest_b_1();
CREATE FUNCTION functest1(a int) RETURNS int LANGUAGE SQL AS 'SELECT $1';
CREATE OR REPLACE FUNCTION functest1(a int) RETURNS int LANGUAGE SQL WINDOW AS 'SELECT $1';
CREATE OR REPLACE PROCEDURE functest1(a int) LANGUAGE SQL AS 'SELECT $1';
DROP FUNCTION functest1(a int);
CREATE FUNCTION voidtest1(a int) RETURNS VOID LANGUAGE SQL AS $$ SELECT a + 1 $$;
CREATE FUNCTION voidtest2(a int, b int) RETURNS VOID LANGUAGE SQL AS $$ SELECT voidtest1(a + b) $$;
CREATE FUNCTION voidtest3(a int) RETURNS VOID LANGUAGE SQL AS $$ INSERT INTO sometable VALUES(a + 1) $$;
CREATE FUNCTION voidtest4(a int) RETURNS VOID LANGUAGE SQL AS $$ INSERT INTO sometable VALUES(a - 1) RETURNING f1 $$;
CREATE FUNCTION voidtest5(a int) RETURNS SETOF VOID LANGUAGE SQL AS $$ SELECT generate_series(1, a) $$ STABLE;
CREATE FUNCTION mylt (text, text) RETURNS boolean LANGUAGE sql AS $$ select $1 < $2 $$;
CREATE FUNCTION mylt_noninline (text, text) RETURNS boolean LANGUAGE sql AS $$ select $1 < $2 limit 1 $$;
CREATE FUNCTION mylt_plpgsql (text, text) RETURNS boolean LANGUAGE plpgsql AS $$ begin return $1 < $2; end $$;
CREATE FUNCTION mylt2 (x text, y text) RETURNS boolean LANGUAGE plpgsql AS $$
declare
  xx text := x;
  yy text := y;
begin
  return xx < yy;
end
$$;

create function sillysrf(int) returns setof int as 'values (1),(10),(2),($1)' language sql immutable;
drop function sillysrf(int);
CREATE FUNCTION retset (a int) RETURNS SETOF int AS $$ SELECT 1; $$ LANGUAGE SQL IMMUTABLE;
DROP FUNCTION retset(int);
CREATE FUNCTION plusone(a int) RETURNS INT AS $$ SELECT a+1; $$ LANGUAGE SQL;
DROP FUNCTION plusone(int);
CREATE FUNCTION my_int4_sort(int4,int4) RETURNS int LANGUAGE sql AS $$ SELECT CASE WHEN $1 = $2 THEN 0 WHEN $1 > $2 THEN 1 ELSE -1 END; $$;
DROP FUNCTION my_int4_sort(int4,int4);
create or replace function func_part_create() returns trigger
  language plpgsql as $$
  begin
    execute 'create table tab_part_create_1 partition of tab_part_create for values in (1)';
    return null;
  end $$;
drop function func_part_create();

CREATE FUNCTION casttesttype_in(cstring) RETURNS casttesttype AS 'textin' LANGUAGE internal STRICT IMMUTABLE;
CREATE FUNCTION casttesttype_out(casttesttype) RETURNS cstring AS 'textout' LANGUAGE internal STRICT IMMUTABLE;
CREATE FUNCTION casttestfunc(casttesttype) RETURNS int4 LANGUAGE SQL AS $$ SELECT 1; $$;
CREATE FUNCTION int4_casttesttype(int4) RETURNS casttesttype LANGUAGE SQL AS $$ SELECT ('foo'::text || $1::text)::casttesttype; $$;
create function least_accum(anyelement, variadic anyarray)
returns anyelement language sql as
  'select least($1, min($2[i])) from generate_subscripts($2,1) g(i)';
-- multi-argument aggregate
create function sum3(int8,int8,int8) returns int8 as 'select $1 + $2 + $3' language sql strict immutable;
CREATE OR REPLACE FUNCTION f_leak(text) RETURNS bool 
    COST 0.0000001 LANGUAGE plpgsql
    AS 'BEGIN RAISE NOTICE ''f_leak => %'', $1; RETURN true; END';
CREATE OR REPLACE FUNCTION hs_subxids (n integer)
RETURNS void
LANGUAGE plpgsql
AS $$
    BEGIN
      IF n <= 0 THEN RETURN; END IF;
      INSERT INTO hs_extreme VALUES (n);
      PERFORM hs_subxids(n - 1);
      RETURN;
    EXCEPTION WHEN raise_exception THEN NULL; END;
$$;
CREATE FUNCTION f_leak (text)
       RETURNS bool LANGUAGE plpgsql COST 0.0000001
       AS 'BEGIN RAISE NOTICE ''f_leak => %'', $1; RETURN true; END';