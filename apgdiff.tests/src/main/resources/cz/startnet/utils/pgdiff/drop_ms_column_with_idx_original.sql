CREATE TABLE [dbo].[table1](
    [c1] [int] NOT NULL,
    [c2] [int] NOT NULL,
    [c3] [varchar](100) NOT NULL)
GO

CREATE NONCLUSTERED INDEX [index_c1] ON [dbo].[table1] ([c1])
GO

CREATE NONCLUSTERED INDEX [index_c2] ON [dbo].[table1] ([c2])
GO