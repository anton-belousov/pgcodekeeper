package cz.startnet.utils.pgdiff.parsers.antlr.statements;

import java.util.ArrayList;
import java.util.List;

import cz.startnet.utils.pgdiff.parsers.antlr.QNameParser;
import cz.startnet.utils.pgdiff.parsers.antlr.SQLParser.Alter_fts_configurationContext;
import cz.startnet.utils.pgdiff.parsers.antlr.SQLParser.Alter_fts_statementContext;
import cz.startnet.utils.pgdiff.parsers.antlr.SQLParser.IdentifierContext;
import cz.startnet.utils.pgdiff.parsers.antlr.SQLParser.Schema_qualified_nameContext;
import cz.startnet.utils.pgdiff.schema.AbstractSchema;
import cz.startnet.utils.pgdiff.schema.GenericColumn;
import cz.startnet.utils.pgdiff.schema.PgDatabase;
import cz.startnet.utils.pgdiff.schema.PgFtsConfiguration;
import cz.startnet.utils.pgdiff.schema.PgStatement;
import ru.taximaxim.codekeeper.apgdiff.model.difftree.DbObjType;

public class AlterFtsStatement extends ParserAbstract {

    private final Alter_fts_statementContext ctx;

    public AlterFtsStatement(Alter_fts_statementContext ctx, PgDatabase db) {
        super(db);
        this.ctx = ctx;
    }

    @Override
    public PgStatement getObject() {
        List<IdentifierContext> ids = ctx.name.identifier();
        AbstractSchema schema = getSchemaSafe(ids, db.getDefaultSchema());
        if (ctx.CONFIGURATION() == null) {
            return null;
        }

        PgFtsConfiguration config = getSafe(schema::getFtsConfiguration,
                QNameParser.getFirstNameCtx(ids));

        Alter_fts_configurationContext afc = ctx.alter_fts_configuration();
        if (afc != null && afc.ADD() != null) {
            for (IdentifierContext type : afc.types) {
                List<String> dics = new ArrayList<>();
                for (Schema_qualified_nameContext dictionary : afc.dictionaries) {
                    List<IdentifierContext> dIds = dictionary.identifier();
                    dics.add(getFullCtxText(dictionary));
                    config.addDep(new GenericColumn(QNameParser.getSchemaName(dIds, getDefSchemaName()),
                            QNameParser.getFirstName(dIds), DbObjType.FTS_DICTIONARY));
                }
                config.addDictionary(getFullCtxText(type), dics);
            }
        }

        return null;
    }
}
