package cz.startnet.utils.pgdiff.parsers.antlr.expr;

import java.util.List;

import org.antlr.v4.runtime.ParserRuleContext;

import cz.startnet.utils.pgdiff.parsers.antlr.SQLParser.VexContext;
import cz.startnet.utils.pgdiff.parsers.antlr.rulectx.Vex;
import cz.startnet.utils.pgdiff.schema.GenericColumn;
import cz.startnet.utils.pgdiff.schema.PgDatabase;
import cz.startnet.utils.pgdiff.schema.PgStatement;
import ru.taximaxim.codekeeper.apgdiff.model.difftree.DbObjType;
import ru.taximaxim.codekeeper.apgdiff.utils.Pair;

public class UtilAnalyzeExpr {

    public static <T extends ParserRuleContext> void analyze(
            T ctx, AbstractExprWithNmspc<T> analyzer, PgStatement pg) {
        analyzer.analyze(ctx);
        pg.addAllDeps(analyzer.getDepcies());
    }

    public static void analyze(VexContext ctx, ValueExpr analyzer, PgStatement pg) {
        analyzer.analyze(new Vex(ctx));
        pg.addAllDeps(analyzer.getDepcies());
    }

    public static void analyzeWithNmspc(VexContext ctx, PgStatement statement,
            String schemaName, String rawTableReference, PgDatabase db) {
        ValueExprWithNmspc valExptWithNmspc = new ValueExprWithNmspc(schemaName, db);
        valExptWithNmspc.addRawTableReference(new GenericColumn(schemaName,
                rawTableReference, DbObjType.TABLE));
        analyze(ctx, valExptWithNmspc, statement);
    }

    public static <T extends ParserRuleContext> void analyzeFuncDefin(
            T ctx, AbstractExprWithNmspc<T> analyzer, PgStatement pg,
            List<Pair<String, String>> params) {
        analyzer.addFuncParams(params);
        analyze(ctx, analyzer, pg);
    }

    private UtilAnalyzeExpr() {
    }
}
