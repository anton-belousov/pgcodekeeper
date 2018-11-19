package cz.startnet.utils.pgdiff.loader.jdbc;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.function.Function;

import org.eclipse.core.runtime.SubMonitor;

import cz.startnet.utils.pgdiff.MsDiffUtils;
import cz.startnet.utils.pgdiff.PgDiffArguments;
import cz.startnet.utils.pgdiff.PgDiffUtils;
import cz.startnet.utils.pgdiff.loader.JdbcConnector;
import cz.startnet.utils.pgdiff.loader.JdbcQueries;
import cz.startnet.utils.pgdiff.loader.JdbcRunner;
import cz.startnet.utils.pgdiff.loader.SupportedVersion;
import cz.startnet.utils.pgdiff.loader.timestamps.DBTimestamp;
import cz.startnet.utils.pgdiff.loader.timestamps.ObjectTimestamp;
import cz.startnet.utils.pgdiff.parsers.antlr.AntlrParser;
import cz.startnet.utils.pgdiff.parsers.antlr.SQLParser;
import cz.startnet.utils.pgdiff.parsers.antlr.TSQLParser;
import cz.startnet.utils.pgdiff.schema.AbstractColumn;
import cz.startnet.utils.pgdiff.schema.AbstractPgFunction;
import cz.startnet.utils.pgdiff.schema.AbstractSchema;
import cz.startnet.utils.pgdiff.schema.AbstractTable;
import cz.startnet.utils.pgdiff.schema.GenericColumn;
import cz.startnet.utils.pgdiff.schema.PgDatabase;
import cz.startnet.utils.pgdiff.schema.PgPrivilege;
import cz.startnet.utils.pgdiff.schema.PgStatement;
import cz.startnet.utils.pgdiff.schema.PgStatementWithSearchPath;
import ru.taximaxim.codekeeper.apgdiff.ApgdiffConsts;
import ru.taximaxim.codekeeper.apgdiff.DaemonThreadFactory;
import ru.taximaxim.codekeeper.apgdiff.model.difftree.DbObjType;

/**
 * Container for shared JdbcLoader state.
 *
 * @author levsha_aa
 */
public abstract class JdbcLoaderBase implements PgCatalogStrings {

    private static final int DEFAULT_OBJECTS_COUNT = 100;
    private static final ExecutorService ANTLR_POOL = Executors.newFixedThreadPool(
            Integer.max(1, Runtime.getRuntime().availableProcessors() - 1),
            new DaemonThreadFactory());

    // TODO after removing helpers split this into MS and PG base classes

    protected final JdbcConnector connector;
    protected final SubMonitor monitor;
    protected final PgDiffArguments args;
    private final Queue<AntlrTask<?>> antlrTasks = new ArrayDeque<>();
    private GenericColumn currentObject;
    private String currentOperation;
    protected Connection connection;
    protected Statement statement;
    private Map<Long, String> cachedRolesNamesByOid;
    protected Map<Long, JdbcType> cachedTypesByOid;
    protected final Map<Long, AbstractSchema> schemaIds = new HashMap<>();
    protected int version;
    private long lastSysOid;
    protected List<String> errors = new ArrayList<>();
    protected JdbcRunner runner;

    protected final TimestampParam timestampParams = new TimestampParam();

    public JdbcLoaderBase(JdbcConnector connector, SubMonitor monitor, PgDiffArguments args) {
        this.connector = connector;
        this.monitor = monitor;
        this.args = args;
        this.runner = new JdbcRunner(monitor);
    }

    protected void setCurrentObject(GenericColumn currentObject) {
        this.currentObject = currentObject;
    }

    protected void setCurrentOperation(String operation) {
        currentObject = null;
        currentOperation = operation;
    }

    protected String getCurrentLocation() {
        StringBuilder sb = new StringBuilder("jdbc:");
        if (currentObject == null) {
            return sb.append(currentOperation).toString();
        } else {
            if (currentObject.schema != null) {
                sb.append('/').append(currentObject.schema);
            }
            if (currentObject.table != null) {
                sb.append('/').append(currentObject.table);
            }
            if (currentObject.column != null) {
                sb.append('/').append(currentObject.column);
            }
        }
        return sb.toString();
    }

    protected void queryRoles() throws SQLException, InterruptedException {
        if (args.isIgnorePrivileges()) {
            return;
        }
        cachedRolesNamesByOid = new HashMap<>();
        setCurrentOperation("roles query");
        try (ResultSet res = runner.runScript(statement, "SELECT oid::bigint, rolname FROM pg_catalog.pg_roles")) {
            while (res.next()) {
                cachedRolesNamesByOid.put(res.getLong(OID), res.getString("rolname"));
            }
        }
    }

    protected void addError(final String message) {
        errors.add(getCurrentLocation() + ' ' + message);
    }

    public List<ObjectTimestamp> getTimestampEqualObjects() {
        return timestampParams.equalObjects;
    }

    public PgDatabase getTimestampProjDb() {
        return timestampParams.projDB;
    }

    public String getExtensionSchema() {
        return timestampParams.extensionSchema;
    }

    protected String getRoleByOid(long oid) {
        if (args.isIgnorePrivileges()) {
            return null;
        }
        return oid == 0 ? "PUBLIC" : cachedRolesNamesByOid.get(oid);
    }

    protected void setOwner(PgStatement statement, long ownerOid) {
        if (!args.isIgnorePrivileges()) {
            statement.setOwner(getRoleByOid(ownerOid));
        }
    }

    protected void setOwner(PgStatement st, String owner) {
        if (!args.isIgnorePrivileges()) {
            st.setOwner(owner);
        }
    }

    public void setPrivileges(PgStatement st, String aclItemsArrayAsString, String schemaName) {
        setPrivileges(st, aclItemsArrayAsString, null, schemaName);
    }

    public void setPrivileges(PgStatement st, String aclItemsArrayAsString, String columnName, String schemaName) {
        String signature;
        switch (st.getStatementType()) {
        case FUNCTION:
        case PROCEDURE:
            signature = ((AbstractPgFunction) st).appendFunctionSignature(
                    new StringBuilder(), false, true).toString();
            break;
        default:
            signature = PgDiffUtils.getQuotedName(st.getName());
            break;
        }

        String owner = st.getOwner();
        if (owner == null && st.getStatementType() == DbObjType.SCHEMA
                && ApgdiffConsts.PUBLIC.equals(st.getName())) {
            owner = "postgres";
        }

        setPrivileges(st, signature, aclItemsArrayAsString, owner,
                columnName == null ? null : PgDiffUtils.getQuotedName(columnName), schemaName);
    }

    public void setPrivileges(AbstractColumn column, AbstractTable t, String aclItemsArrayAsString, String schemaName) {
        setPrivileges(column, PgDiffUtils.getQuotedName(t.getName()), aclItemsArrayAsString,
                t.getOwner(), PgDiffUtils.getQuotedName(column.getName()), schemaName);
    }

    /**
     * Parses <code>aclItemsArrayAsString</code> and adds parsed privileges to
     * <code>PgStatement</code> object. Owner privileges go first.
     * <br>
     * Currently supports privileges only on PgSequence, PgTable, PgView, PgColumn,
     * PgFunction, PgSchema, PgType, PgDomain
     *
     * @param st    PgStatement object where privileges to be added
     * @param stSignature   PgStatement signature (differs in different PgStatement instances)
     * @param aclItemsArrayAsString     Input acl string in the
     *                                  form of "{grantee=grant_chars/grantor[, ...]}"
     * @param owner the owner of PgStatement object (why separate?)
     * @param column    column name, if this aclItemsArrayAsString is column
     *                      privilege string; otherwise null
     * @param schemaName name of schema for 'PgStatement st'
     */
    /*
     * See parseAclItem() in dumputils.c
     * For privilege characters see JdbcAclParser.PrivilegeTypes
     * Order of all characters (for all types of objects combined) : raxdtDXCcTUw
     */
    private void setPrivileges(PgStatement st, String stSignature,
            String aclItemsArrayAsString, String owner, String columnId, String schemaName) {
        if (aclItemsArrayAsString == null || args.isIgnorePrivileges()) {
            return;
        }
        DbObjType type = st.getStatementType();
        String stType = null;
        boolean isFunctionOrTypeOrDomain = false;
        String order;
        switch (type) {
        case SEQUENCE:
            order = "rUw";
            break;

        case TABLE:
        case VIEW:
        case COLUMN:
            stType = "TABLE";
            if (columnId == null) {
                order = "raxdtDw";
            } else {
                order = "raxw";
            }
            break;

        case FUNCTION:
            order = "X";
            isFunctionOrTypeOrDomain = true;
            break;

        case SCHEMA:
            order = "CU";
            break;

        case TYPE:
        case DOMAIN:
            stType = "TYPE";
            order = "U";
            isFunctionOrTypeOrDomain = true;
            break;

        default:
            throw new IllegalStateException(st.getStatementType() + " doesn't support privileges!");
        }
        int possiblePrivilegeCount = order.length();
        if (stType == null) {
            stType = st.getStatementType().name();
        }

        String qualStSignature = schemaName == null ? stSignature
                : PgDiffUtils.getQuotedName(schemaName) + '.' + stSignature;
        String column = (columnId != null && !columnId.isEmpty()) ? "(" + columnId + ")" : "";

        List<Privilege> grants = JdbcAclParser.parse(
                aclItemsArrayAsString, possiblePrivilegeCount, order, owner);

        boolean metPublicRoleGrants = false;
        boolean metDefaultOwnersGrants = false;
        for (Privilege p : grants) {
            if (p.isGrantAllToPublic()) {
                metPublicRoleGrants = true;
            }
            if (p.isDefault) {
                metDefaultOwnersGrants = true;
            }
        }

        // FUNCTION/TYPE/DOMAIN by default has "GRANT ALL to PUBLIC".
        // If "GRANT ALL to PUBLIC" for FUNCTION/TYPE/DOMAIN is absent, then
        // in this case for them explicitly added "REVOKE ALL from PUBLIC".
        if (!metPublicRoleGrants && isFunctionOrTypeOrDomain) {
            st.addPrivilege(new PgPrivilege("REVOKE", "ALL" + column,
                    stType + " " + qualStSignature, "PUBLIC", false));
        }

        // 'REVOKE ALL' for COLUMN never happened, because of the overlapping
        // privileges from the table.
        if (DbObjType.COLUMN != type && !metDefaultOwnersGrants) {
            st.addPrivilege(new PgPrivilege("REVOKE", "ALL" + column,
                    stType + " " + qualStSignature, PgDiffUtils.getQuotedName(owner), false));
        }

        for (Privilege grant : grants) {
            boolean isViewWithColPrivil = DbObjType.VIEW == type
                    && column != null && !column.isEmpty();

            // Skip if statement is VIEW with column privilege, because
            // such case is shown in pg_dumn.
            //
            // Skip if statement type is COLUMN, because of the specific
            // relationship with table privileges.
            // The privileges of columns for role are not set lower than for the
            // same role in the parent table, they may be the same or higher.
            //
            // Skip if default owner's privileges
            // or if it is 'GRANT ALL ON FUNCTION/TYPE/DOMAIN schema.name TO PUBLIC'
            if (!isViewWithColPrivil && DbObjType.COLUMN != type
                    && (grant.isDefault || (isFunctionOrTypeOrDomain && grant.isGrantAllToPublic()))) {
                continue;
            }
            List<String> grantValues = grant.grantValues;
            if (column != null && !column.isEmpty()) {
                grantValues = new ArrayList<>(grant.grantValues.size());
                for (String plainGrant : grant.grantValues) {
                    grantValues.add(plainGrant + column);
                }
            }

            st.addPrivilege(new PgPrivilege("GRANT", String.join(",", grantValues),
                    stType + " " + qualStSignature, grant.grantee, grant.isGO));
        }
    }

    public void setPrivileges(PgStatement st, List<XmlReader> privs) throws XmlReaderException {
        if (args.isIgnorePrivileges()) {
            return;
        }

        for (XmlReader acl : privs) {
            String state = acl.getString("sd");
            boolean isWithGrantOption = false;
            if ("GRANT_WITH_GRANT_OPTION".equals(state)) {
                state = "GRANT";
                isWithGrantOption = true;
            }

            String permission = acl.getString("pn");
            String role = acl.getString("r");
            String col = null;
            StringBuilder sb = new StringBuilder();

            if (st instanceof PgStatementWithSearchPath) {
                col = acl.getString("c");
                PgStatementWithSearchPath pswsp = (PgStatementWithSearchPath) st;

                sb.append(MsDiffUtils.quoteName(pswsp.getContainingSchema().getName()))
                .append('.').append(MsDiffUtils.quoteName(st.getBareName()));

                if (col != null) {
                    sb.append('(').append(MsDiffUtils.quoteName(col)).append(')');
                }
            } else {
                sb.append(st.getStatementType() + "::" + MsDiffUtils.quoteName(st.getName()));
            }

            PgPrivilege priv = new PgPrivilege(state, permission, sb.toString(),
                    MsDiffUtils.quoteName(role), isWithGrantOption);

            if (col != null && st instanceof AbstractTable) {
                ((AbstractTable) st).getColumn(col).addPrivilege(priv);
            } else {
                st.addPrivilege(priv);
            }
        }
    }

    protected void queryTypesForCache() throws SQLException, InterruptedException {
        cachedTypesByOid = new HashMap<>();
        setCurrentOperation("type cache query");
        try (ResultSet res = runner.runScript(statement, JdbcQueries.QUERY_TYPES_FOR_CACHE_ALL)) {
            while (res.next()) {
                long oid = res.getLong(OID);
                JdbcType type = new JdbcType(oid, res.getString("typname"),
                        res.getLong("typelem"), res.getLong("typarray"),
                        res.getString(NAMESPACE_NSPNAME), res.getString("elemname"), lastSysOid);
                cachedTypesByOid.put(oid, type);
            }
        }
    }

    protected void queryCheckVersion() throws SQLException, InterruptedException {
        setCurrentOperation("version checking query");
        try (ResultSet res = runner.runScript(statement, JdbcQueries.QUERY_CHECK_VERSION)) {
            version = res.next() ? res.getInt(1) : SupportedVersion.VERSION_9_2.getVersion();
        }
    }

    protected void queryCheckLastSysOid() throws SQLException, InterruptedException {
        setCurrentOperation("last system oid checking query");
        try (ResultSet res = runner.runScript(statement, JdbcQueries.QUERY_CHECK_LAST_SYS_OID)) {
            lastSysOid = res.next() ? res.getLong(1) : 10_000;
        }
    }

    protected void setupMonitorWork() throws SQLException, InterruptedException {
        setCurrentOperation("object count query");
        try (ResultSet resCount = runner.runScript(statement, JdbcQueries.QUERY_TOTAL_OBJECTS_COUNT)) {
            monitor.setWorkRemaining(resCount.next() ? resCount.getInt(1) : DEFAULT_OBJECTS_COUNT);
        }
    }

    protected <T> void submitAntlrTask(String sql,
            Function<SQLParser, T> parserCtxReader, Consumer<T> finalizer) {
        String loc = getCurrentLocation();
        Future<T> future = ANTLR_POOL.submit(() -> parserCtxReader.apply(
                AntlrParser.makeBasicParser(SQLParser.class, sql, loc)));
        antlrTasks.add(new AntlrTask<>(future, finalizer, currentObject));
    }

    protected <T> void submitMsAntlrTask(String sql,
            Function<TSQLParser, T> parserCtxReader, Consumer<T> finalizer) {
        String loc = getCurrentLocation();
        Future<T> future = ANTLR_POOL.submit(() -> parserCtxReader.apply(
                AntlrParser.makeBasicParser(TSQLParser.class, sql, loc)));
        antlrTasks.add(new AntlrTask<>(future, finalizer, currentObject));
    }

    protected void finishAntlr() throws InterruptedException, ExecutionException {
        AntlrTask<?> task;
        setCurrentOperation("finalizing antlr");
        while ((task = antlrTasks.poll()) != null) {
            // default to operation if object is null
            setCurrentObject(task.object);
            task.finish();
        }
    }

    protected static class TimestampParam {
        private List<ObjectTimestamp> equalObjects;
        private PgDatabase projDB;
        private String extensionSchema;

        public void setTimeParams(PgDatabase projDB, String extensionSchema) {
            this.projDB = projDB;
            this.extensionSchema = extensionSchema;
        }

        public void fillEqualObjects(DBTimestamp dbTime) {
            equalObjects = projDB.getDbTimestamp().searchEqualsObjects(dbTime);
        }
    }
}
