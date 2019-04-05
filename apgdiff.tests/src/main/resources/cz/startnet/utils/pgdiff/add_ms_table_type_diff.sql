CREATE TYPE [dbo].[type1] AS TABLE(
	[ct1] [int] NOT NULL,
	[ct2] [int] NOT NULL,
	INDEX [q] NONCLUSTERED HASH
(
	[ID] ASC
) WITH ( BUCKET_COUNT = 2),
	PRIMARY KEY NONCLUSTERED HASH
(
    [c2] ASC
) WITH ( BUCKET_COUNT = 2),
	CHECK ((c1 > 0))
)
WITH ( MEMORY_OPTIMIZED = ON )
GO
