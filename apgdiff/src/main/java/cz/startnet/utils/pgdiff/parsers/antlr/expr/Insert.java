package cz.startnet.utils.pgdiff.parsers.antlr.expr;

import java.util.Collections;
import java.util.List;

import cz.startnet.utils.pgdiff.parsers.antlr.SQLParser.Insert_columnsContext;
import cz.startnet.utils.pgdiff.parsers.antlr.SQLParser.Insert_stmt_for_psqlContext;
import cz.startnet.utils.pgdiff.parsers.antlr.SQLParser.Select_stmtContext;
import cz.startnet.utils.pgdiff.parsers.antlr.SQLParser.With_clauseContext;
import cz.startnet.utils.pgdiff.schema.IDatabase;
import ru.taximaxim.codekeeper.apgdiff.model.difftree.DbObjType;
import ru.taximaxim.codekeeper.apgdiff.utils.ModPair;

public class Insert extends AbstractExprWithNmspc<Insert_stmt_for_psqlContext> {

    protected Insert(AbstractExpr parent) {
        super(parent);
    }

    public Insert(IDatabase db, DbObjType... disabledDepcies) {
        super(db, disabledDepcies);
    }

    @Override
    public List<ModPair<String, String>> analyze(Insert_stmt_for_psqlContext insert) {
        // this select is used to collect namespaces for this INSERT operation
        Select select = new Select(this);
        With_clauseContext with = insert.with_clause();
        if (with != null) {
            select.analyzeCte(with);
        }

        select.addNameReference(insert.insert_table_name, null, null);

        Insert_columnsContext columns = insert.insert_columns();
        if (columns != null) {
            select.addColumnsDepcies(insert.insert_table_name, columns.indirection_identifier());
        }

        Select_stmtContext selectCtx = insert.select_stmt();
        if (selectCtx != null) {
            new Select(select).analyze(selectCtx);
        }

        if (insert.RETURNING() != null) {
            return select.sublist(insert.select_list().select_sublist(), new ValueExpr(select));
        }

        return Collections.emptyList();
    }
}
