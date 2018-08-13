CREATE SEQUENCE [dbo].[seq1]
    START WITH 555999
    INCREMENT BY 777
    MAXVALUE 5558882036854775807
    MINVALUE 555888
    CACHE 
GO

CREATE TABLE [dbo].[table1](
    [c1] [bigint] NOT NULL,
    [c2] [int] NOT NULL,
    [c3] [varchar](100) NOT NULL)
GO

ALTER TABLE [dbo].[table1] 
    ADD CONSTRAINT [PK_table1] PRIMARY KEY CLUSTERED  ([c1]) ON [PRIMARY]
GO

ALTER TABLE [dbo].[table1]
    ADD CONSTRAINT [constraint_default_c1] DEFAULT (NEXT VALUE FOR [dbo].[seq1]) FOR [c1]
GO