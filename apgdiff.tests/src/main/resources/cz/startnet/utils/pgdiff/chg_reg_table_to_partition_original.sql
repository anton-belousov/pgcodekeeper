CREATE TABLE public.cities (
    c1 integer,
    c2 text,
    c3 bigint
)
PARTITION BY LIST ("left"(lower(c2), 1));

ALTER TABLE public.cities OWNER TO galiev_mr;

CREATE TABLE public.cities_ab  (
    c1 integer,
    c2 text,
    c3 bigint,
    CONSTRAINT constr_check CHECK ((c1 > 10))
);

ALTER TABLE public.cities_ab OWNER TO galiev_mr;
