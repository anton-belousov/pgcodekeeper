--
-- PostgreSQL database dump
--

SET statement_timeout = 0;
SET lock_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SET check_function_bodies = false;
SET client_min_messages = warning;

--
-- Name: plpgsql; Type: EXTENSION; Schema: -; Owner: 
--

--CREATE EXTENSION IF NOT EXISTS plpgsql WITH SCHEMA pg_catalog;


--
-- Name: EXTENSION plpgsql; Type: COMMENT; Schema: -; Owner: 
--

--COMMENT ON EXTENSION plpgsql IS 'PL/pgSQL procedural language';


SET search_path = pg_catalog;

SET default_tablespace = '';

SET default_with_oids = false;

--
-- Name: f1(text); Type: FUNCTION; Schema: public; Owner: shamsutdinov_lr
--

CREATE OR REPLACE FUNCTION public.f1(k text) RETURNS text
    LANGUAGE sql
    AS $$ SELECT 'oldtext'::text; $$;

ALTER FUNCTION public.f1(k text) OWNER TO shamsutdinov_lr;

--
-- Name: f2(text); Type: FUNCTION; Schema: public; Owner: shamsutdinov_lr
--

CREATE OR REPLACE FUNCTION public.f2(j text) RETURNS text
    LANGUAGE sql
    AS $$ select public.f1('newtext'::text); $$;

ALTER FUNCTION public.f2(j text) OWNER TO shamsutdinov_lr;


--
-- Name: public; Type: ACL; Schema: -; Owner: postgres
--

REVOKE ALL ON SCHEMA public FROM PUBLIC;
REVOKE ALL ON SCHEMA public FROM postgres;
GRANT ALL ON SCHEMA public TO postgres;
GRANT ALL ON SCHEMA public TO PUBLIC;


--
-- PostgreSQL database dump complete
--

