package cz.startnet.utils.pgdiff.parsers.antlr.expr;

import java.util.Arrays;
import java.util.List;

import cz.startnet.utils.pgdiff.parsers.antlr.SQLParser.VexContext;
import cz.startnet.utils.pgdiff.parsers.antlr.SQLParser.Vex_bContext;
import cz.startnet.utils.pgdiff.parsers.antlr.rulectx.Vex;
import cz.startnet.utils.pgdiff.schema.IDatabase;
import ru.taximaxim.codekeeper.apgdiff.model.difftree.DbObjType;
import ru.taximaxim.codekeeper.apgdiff.utils.ModPair;

/**
 * For use with value expressions that have predefined namespace.
 * @author levsha_aa
 */
public class ValueExprWithNmspc extends AbstractExprWithNmspc<VexContext> {

    private final ValueExpr vex;

    public ValueExprWithNmspc(IDatabase db, DbObjType... disabledDepcies) {
        super(db, disabledDepcies);
        vex = new ValueExpr(this);
    }

    @Override
    public List<ModPair<String, String>> analyze(VexContext vex) {
        return analyze(new Vex(vex));
    }

    public List<ModPair<String, String>> analyze(Vex_bContext vex) {
        return analyze(new Vex(vex));
    }

    public List<ModPair<String, String>> analyze(Vex vex) {
        return Arrays.asList(this.vex.analyze(vex));
    }
}
