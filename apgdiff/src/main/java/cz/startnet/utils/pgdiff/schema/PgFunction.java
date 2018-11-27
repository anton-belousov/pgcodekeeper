/**
 * Copyright 2006 StartNet s.r.o.
 *
 * Distributed under MIT license
 */
package cz.startnet.utils.pgdiff.schema;

import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import cz.startnet.utils.pgdiff.PgDiffUtils;
import ru.taximaxim.codekeeper.apgdiff.model.difftree.DbObjType;

/**
 * Stores Postgres function information.
 */
public class PgFunction extends AbstractPgFunction {

    @Override
    public DbObjType getStatementType() {
        return DbObjType.FUNCTION;
    }

    public PgFunction(String name, String rawStatement) {
        super(name, rawStatement);
    }

    @Override
    public String getCreationSQL() {
        final StringBuilder sbSQL = new StringBuilder();
        sbSQL.append("CREATE OR REPLACE FUNCTION ");
        sbSQL.append(PgDiffUtils.getQuotedName(getContainingSchema().getName())).append('.');
        appendFunctionSignature(sbSQL, true, true);
        sbSQL.append(' ');
        sbSQL.append("RETURNS ");
        sbSQL.append(getReturns());
        sbSQL.append("\n    ");

        if (getLanguage() != null) {
            sbSQL.append("LANGUAGE ").append(PgDiffUtils.getQuotedName(getLanguage()));
        }

        if (!transforms.isEmpty()) {
            sbSQL.append(" TRANSFORM ");
            for (String tran : transforms) {
                sbSQL.append("FOR TYPE ").append(tran).append(", ");
            }

            sbSQL.setLength(sbSQL.length() - 2);
        }

        if (isWindow()) {
            sbSQL.append(" WINDOW");
        }

        if (getVolatileType() != null) {
            sbSQL.append(' ').append(getVolatileType());
        }

        if (isStrict()) {
            sbSQL.append(" STRICT");
        }

        if (isSecurityDefiner()) {
            sbSQL.append(" SECURITY DEFINER");
        }

        if (isLeakproof()) {
            sbSQL.append(" LEAKPROOF");
        }

        if (getParallel() != null) {
            sbSQL.append(" PARALLEL ").append(getParallel());
        }

        if ("internal".equals(getLanguage()) || "c".equals(getLanguage())) {
            /* default cost is 1 */
            if (1.0f != getCost()) {
                sbSQL.append(" COST ");
                if (getCost() % 1 == 0) {
                    sbSQL.append((int)getCost());
                } else {
                    sbSQL.append(getCost());
                }
            }
        } else {
            /* default cost is 100 */
            if (DEFAULT_PROCOST != getCost()) {
                sbSQL.append(" COST ");
                if (getCost() % 1 == 0) {
                    sbSQL.append((int)getCost());
                } else {
                    sbSQL.append(getCost());
                }
            }
        }

        if (DEFAULT_PROROWS != getRows()) {
            sbSQL.append(" ROWS ");
            if (getRows() % 1 == 0) {
                sbSQL.append((int)getRows());
            } else {
                sbSQL.append(getRows());
            }
        }

        if (!configurations.isEmpty()) {
            for (Entry<String, String> param : configurations.entrySet()) {
                String val = param.getValue();
                sbSQL.append("\n    SET ").append(param.getKey());
                if (FROM_CURRENT.equals(val)) {
                    sbSQL.append(val);
                } else {
                    sbSQL.append(" TO ").append(val);
                }
            }
        }

        sbSQL.append("\n    AS ");
        sbSQL.append(getBody());
        sbSQL.append(';');

        appendOwnerSQL(sbSQL);
        appendPrivileges(sbSQL);

        if (comment != null && !comment.isEmpty()) {
            sbSQL.append("\n\n");
            appendCommentSql(sbSQL);
        }

        return sbSQL.toString();
    }

    @Override
    public String getDeclaration(Argument arg, boolean includeDefaultValue, boolean includeArgName) {
        final StringBuilder sbString = new StringBuilder();

        String mode = arg.getMode();
        if (mode != null && !"IN".equalsIgnoreCase(mode)) {
            sbString.append(mode);
            sbString.append(' ');
        }

        String name = arg.getName();

        if (name != null && !name.isEmpty() && includeArgName) {
            sbString.append(PgDiffUtils.getQuotedName(name));
            sbString.append(' ');
        }

        sbString.append(arg.getDataType());

        String def = arg.getDefaultExpression();

        if (includeDefaultValue && def != null && !def.isEmpty()) {
            sbString.append(" = ");
            sbString.append(def);
        }

        return sbString.toString();
    }

    @Override
    public String getDropSQL() {
        final StringBuilder sbString = new StringBuilder();
        sbString.append("DROP FUNCTION ");
        sbString.append(PgDiffUtils.getQuotedName(getContainingSchema().getName())).append('.');
        appendFunctionSignature(sbString, false, true);
        sbString.append(';');

        return sbString.toString();
    }

    @Override
    public boolean appendAlterSQL(PgStatement newCondition, StringBuilder sb,
            AtomicBoolean isNeedDepcies) {
        final int startLength = sb.length();
        PgFunction newFunction;
        if (newCondition instanceof PgFunction) {
            newFunction = (PgFunction)newCondition;
        } else {
            return false;
        }

        if (!checkForChanges(newFunction)) {
            if (needDrop(newFunction)) {
                isNeedDepcies.set(true);
                return true;
            } else {
                sb.append(newFunction.getCreationSQL());
            }
        }

        if (!Objects.equals(getOwner(), newFunction.getOwner())) {
            newFunction.alterOwnerSQL(sb);
        }
        alterPrivileges(newFunction, sb);
        if (!Objects.equals(getComment(), newFunction.getComment())) {
            sb.append("\n\n");
            newFunction.appendCommentSql(sb);
        }
        return sb.length() > startLength;
    }

    private boolean needDrop(AbstractFunction newFunction) {
        if (newFunction == null ||
                !Objects.equals(getReturns(), newFunction.getReturns())) {
            return true;
        }

        Iterator<Argument> iOld = arguments.iterator();
        Iterator<Argument> iNew = newFunction.arguments.iterator();
        while (iOld.hasNext() && iNew.hasNext()) {
            Argument argOld = iOld.next();
            Argument argNew = iNew.next();

            String oldDef = argOld.getDefaultExpression();
            String newDef = argNew.getDefaultExpression();
            // allow creation of defaults (old==null && new!=null)
            if (oldDef != null && !oldDef.equals(newDef)) {
                return true;
            }

            // [IN]OUT args that change their names implicitly change the function's
            // return type due to it being "SETOF record" in case of
            // multiple [IN]OUT args present

            // actually any argument name change requires drop
            if (!Objects.equals(argOld.getName(), argNew.getName())) {
                return true;
            }
            // нельзя менять тип out параметров
            if ("OUT".equalsIgnoreCase(argOld.getMode()) &&
                    !Objects.equals(argOld.getDataType(), argNew.getDataType())) {
                return true;
            }
        }
        // Если добавляется или удаляется out параметр нужно удалить функцию,
        // т.к. меняется её возвращаемое значение
        while (iOld.hasNext()) {
            if ("OUT".equalsIgnoreCase(iOld.next().getMode())) {
                return true;
            }
        }
        while (iNew.hasNext()) {
            if ("OUT".equalsIgnoreCase(iNew.next().getMode())) {
                return true;
            }
        }

        return false;
    }

    /**
     * Alias for {@link #getSignature()} which provides a unique function ID.
     *
     * Use {@link #getBareName()} to get just the function name.
     */
    @Override
    public String getName() {
        return getSignature();
    }

    @Override
    public String getQualifiedName() {
        return getParent().getQualifiedName() + '.' + getName();
    }

    @Override
    protected AbstractFunction getFunctionCopy() {
        return new PgFunction(getBareName(), getRawStatement());
    }
}
