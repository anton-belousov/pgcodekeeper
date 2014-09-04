/**
 * Copyright 2006 StartNet s.r.o.
 *
 * Distributed under MIT license
 */
package cz.startnet.utils.pgdiff;

import java.io.InputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;

import org.jgrapht.DirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.traverse.DepthFirstIterator;

import ru.taximaxim.codekeeper.apgdiff.Log;
import ru.taximaxim.codekeeper.apgdiff.model.graph.DepcyGraph;
import cz.startnet.utils.pgdiff.loader.PgDumpLoader;
import cz.startnet.utils.pgdiff.schema.GenericColumn;
import cz.startnet.utils.pgdiff.schema.PgDatabase;
import cz.startnet.utils.pgdiff.schema.PgExtension;
import cz.startnet.utils.pgdiff.schema.PgSchema;
import cz.startnet.utils.pgdiff.schema.PgSequence;
import cz.startnet.utils.pgdiff.schema.PgStatement;
import cz.startnet.utils.pgdiff.schema.PgTable;
import cz.startnet.utils.pgdiff.schema.PgView;

/**
 * Creates diff of two database schemas.
 *
 * @author fordfrog
 */
public class PgDiff {

    private static DirectedGraph<PgStatement, DefaultEdge> oldDepcyGraph;
    private static DirectedGraph<PgStatement, DefaultEdge> newDepcyGraph;
    private static DepcyGraph depcyOld;
    private static DepcyGraph depcyNew;
    
    private static PgDatabase dbNew;
    private static PgDatabase dbOld;

    /**
     * Creates diff on the two database schemas.
     *
     * @param writer    writer the output should be written to
     * @param arguments object containing arguments settings
     */
    public static void createDiff(final PrintWriter writer,
            final PgDiffArguments arguments) {
        PgDatabase dbOld = loadDatabaseSchema(
                arguments.getOldSrcFormat(), arguments.getOldSrc(), arguments);
        PgDatabase dbNew = loadDatabaseSchema(
                arguments.getNewSrcFormat(), arguments.getNewSrc(), arguments); 
        diffDatabaseSchemas(writer, arguments, dbOld, dbNew, dbOld, dbNew);
    }

    /**
     * Creates diff on the two database schemas.
     *
     * @param writer         writer the output should be written to
     * @param arguments      object containing arguments settings
     * @param oldInputStream input stream of file containing dump of the
     *                       original schema
     * @param newInputStream input stream of file containing dump of the new
     *                       schema
     */
    public static void createDiff(final PrintWriter writer,
            final PgDiffArguments arguments, final InputStream oldInputStream,
            final InputStream newInputStream) {
        final PgDatabase oldDatabase = PgDumpLoader.loadDatabaseSchemaFromDump(
                oldInputStream, arguments.getInCharsetName(),
                arguments.isOutputIgnoredStatements(),
                arguments.isIgnoreSlonyTriggers());
        final PgDatabase newDatabase = PgDumpLoader.loadDatabaseSchemaFromDump(
                newInputStream, arguments.getInCharsetName(),
                arguments.isOutputIgnoredStatements(),
                arguments.isIgnoreSlonyTriggers());

        diffDatabaseSchemas(writer, arguments, oldDatabase, newDatabase,
                oldDatabase, newDatabase);
    }
    
    /**
     * Loads database schema choosing the provided method.
     * 
     * @param format        format of the database source, must be "dump", "parsed" or "db"
     *                         otherwise exception is thrown
     * @param srcPath        path to the database source to load
     * @param arguments        object containing arguments settings
     * 
     * @return the loaded database
     */
    static PgDatabase loadDatabaseSchema(final String format, final String srcPath,
                final PgDiffArguments arguments) {
        if(format.equals("dump")) {
            return PgDumpLoader.loadDatabaseSchemaFromDump(srcPath,
                    arguments.getInCharsetName(), arguments.isOutputIgnoredStatements(),
                    arguments.isIgnoreSlonyTriggers());
        } else if(format.equals("parsed")) {
            return PgDumpLoader.loadDatabaseSchemaFromDirTree(srcPath,
                    arguments.getInCharsetName(), arguments.isOutputIgnoredStatements(),
                    arguments.isIgnoreSlonyTriggers());
        } else if(format.equals("db")) {
            throw new UnsupportedOperationException("DB connection is not yet implemented!");
        }
        
        throw new UnsupportedOperationException("Unknown DB format!");
    }
    
    /**
     * Creates diff from comparison of two database schemas.<br><br>
     * Following PgDiffArguments methods are called from this method:<br>
     * isAddTransaction()<br>
     * isOutputIgnoredStatements()<br>
     * isIgnoreStartWith()<br>
     * isAddDefaults()<br>
     * isIgnoreFunctionWhitespace()<br>
     *
     * @param writer      writer the output should be written to
     * @param arguments   object containing arguments settings
     * @param oldDatabase original database schema
     * @param newDatabase new database schema
     */
    public static void diffDatabaseSchemas(PrintWriter writer,
            PgDiffArguments arguments, PgDatabase oldDatabase, PgDatabase newDatabase,
            PgDatabase oldDbFull, PgDatabase newDbFull) {
        diffDatabaseSchemasAdditionalDepcies(writer, arguments,
                oldDatabase, newDatabase, oldDbFull, newDbFull, null);
    }
    
    public static void diffDatabaseSchemasAdditionalDepcies(PrintWriter writer,
            PgDiffArguments arguments, PgDatabase oldDatabase, PgDatabase newDatabase,
            PgDatabase oldDbFull, PgDatabase newDbFull,
            List<Entry<PgStatement, PgStatement>> additionalDepcies) {
        // since we cannot into OOP here - null the global vars at least
        oldDepcyGraph = newDepcyGraph = null;
        depcyOld = depcyNew = null;
        
        if (arguments.isAddTransaction()) {
            writer.println("START TRANSACTION;");
        }

        // temp solution
        if (oldDbFull != null && newDbFull != null){
            depcyOld = new DepcyGraph(oldDbFull);
            depcyNew = new DepcyGraph(newDbFull);
            dbOld = oldDbFull;
            dbNew = newDbFull;
            
            if (additionalDepcies != null) {
                depcyOld.addCustomDepcies(additionalDepcies);
            }
        }else{
            depcyOld = new DepcyGraph(new PgDatabase());
            depcyNew = new DepcyGraph(new PgDatabase());
        }
        oldDepcyGraph = depcyOld.getGraph();
        newDepcyGraph = depcyNew.getGraph();
        
        if (oldDatabase.getComment() == null
                && newDatabase.getComment() != null
                || oldDatabase.getComment() != null
                && newDatabase.getComment() != null
                && !oldDatabase.getComment().equals(newDatabase.getComment())) {
            writer.println();
            writer.print("COMMENT ON DATABASE current_database() IS ");
            writer.print(newDatabase.getComment());
            writer.println(';');
            writer.println();
        } else if (oldDatabase.getComment() != null
                && newDatabase.getComment() == null) {
            writer.println();
            writer.println("COMMENT ON DATABASE current_database() IS NULL;");
            writer.println();
        }

        PgDiffScript script = new PgDiffScript();
        
        dropOldSchemas(script, arguments, oldDatabase, newDatabase);
        createNewSchemas(script, oldDatabase, newDatabase);
        
        dropOldExtensions(script, oldDatabase, newDatabase);
        createNewExtensions(script, oldDatabase, newDatabase);
        updateExtensions(script, oldDatabase, newDatabase);
        
        updateSchemas(script, arguments, oldDatabase, newDatabase);

        script.printStatements(writer);
        
        if (arguments.isAddTransaction()) {
            writer.println();
            writer.println("COMMIT TRANSACTION;");
        }

        if (arguments.isOutputIgnoredStatements()) {
            if (!oldDatabase.getIgnoredStatements().isEmpty()) {
                writer.println();
                writer.print("/* ");
                writer.println(Resources.getString(
                        "OriginalDatabaseIgnoredStatements"));

                for (final String statement :
                        oldDatabase.getIgnoredStatements()) {
                    writer.println();
                    writer.println(statement);
                }

                writer.println("*/");
            }

            if (!newDatabase.getIgnoredStatements().isEmpty()) {
                writer.println();
                writer.print("/* ");
                writer.println(
                        Resources.getString("NewDatabaseIgnoredStatements"));

                for (final String statement :
                        newDatabase.getIgnoredStatements()) {
                    writer.println();
                    writer.println(statement);
                }

                writer.println("*/");
            }
        }
    }
    
    /**
     * Fills in the result list with all PgStatements, that are 
     * dependent from parent
     * <br>
     * (that is, finds those vertices, that have common edge with parent where 
     * parent is the target)
     */
    public static Set<PgStatement> getDependantsSet(PgStatement parent,
            Set<PgStatement> result) {
        return getDependants(parent, result, depcyOld);
    }

    /**
     * Fills in the result list with all PgStatements, that are 
     * dependent from parent <b>based on DepcyGraph</b>
     * <br>
     * (that is, finds those vertices, that have common edge with parent where 
     * parent is the target)
     */
    public static Set<PgStatement> getDependantsSet(PgStatement parent,
            Set<PgStatement> result, DepcyGraph graph) {
        return getDependants(parent, result, graph);
    }
    
    private static Set<PgStatement> getDependants(PgStatement parent,
            Set<PgStatement> result, DepcyGraph graph) {
        DirectedGraph<PgStatement, DefaultEdge> depcyGraph = graph.getGraph();
        if (depcyGraph.containsVertex(parent)){
            for (DefaultEdge edge : depcyGraph.incomingEdgesOf(parent)) {
                PgStatement dependant = depcyGraph.getEdgeSource(edge);
                boolean alreadyProcessed = result.contains(dependant);
                if (alreadyProcessed) {
                    DepthFirstIterator<PgStatement, DefaultEdge> iter = 
                            new DepthFirstIterator<PgStatement, DefaultEdge>(graph.getReversedGraph(), dependant);
                    while(iter.hasNext()){
                        PgStatement next = iter.next();
                        result.remove(next);
                        result.add(next);
                    }
                }else {
                    result.add(dependant);
                    getDependants(dependant, result, graph);
                }
            }
        }
        return result;
    }

    /**
     * Fills in the result list with all PgStatements, that child is dependent from.
     * <br>
     * (that is, finds those vertices, that have common edge with child where 
     * child is the source)
     */
    public static Set<PgStatement> getDependenciesSet(PgStatement child,
            Set<PgStatement> result, boolean firstLevelSearchOnly){
        return getDependencies(child, result, firstLevelSearchOnly, newDepcyGraph);
    }
    
    /**
     * Fills in the result list with all PgStatements, that child is dependent from <b>based on depcyGraph</b>.
     * <br>
     * (that is, finds those vertices, that have common edge with child where 
     * child is the source)
     */
    public static Set<PgStatement> getDependenciesSet(PgStatement child,
            Set<PgStatement> result, boolean firstLevelSearchOnly, DirectedGraph<PgStatement, DefaultEdge> depcyGraph){
        return getDependencies(child, result, firstLevelSearchOnly, depcyGraph);
    }
    
    private static Set<PgStatement> getDependencies(PgStatement child,
            Set<PgStatement> result, boolean firstLevelSearchOnly, DirectedGraph<PgStatement, DefaultEdge> depcyGraph) {
        if (depcyGraph.containsVertex(child)){
            for (DefaultEdge edge : depcyGraph.outgoingEdgesOf(child)){
                PgStatement dependency = depcyGraph.getEdgeTarget(edge);
                if (result.add(dependency) && !firstLevelSearchOnly) {
                    getDependencies(dependency, result, firstLevelSearchOnly, depcyGraph);
                }
            }
        }
        return result;
    }
    
    /**
     * Checks, whether the child is in a subtree of the parent.
     * <br>
     * (as trigger would be a child of a table)
     * 
     * TODO add more class checking for parent
     */
    public static boolean containsChild(PgStatement parent, PgStatement child) {
        String name = child.getName();
        if (parent instanceof PgTable){
            PgTable t = (PgTable)parent;
            return t.containsConstraint(name) ||
                   t.containsColumn(name) || 
                   t.containsIndex(name) || 
                   t.containsTrigger(name);
        }else if (parent instanceof PgSchema){
            PgSchema s = (PgSchema) parent;
            return s.containsFunction(name) ||
                   s.containsSequence(name) || 
                   s.containsTable(name) || 
                   s.containsView(name);
        }else{
            Log.log(Log.LOG_DEBUG, "Error in PgDiff.containsChild: parent is neither "
                    + "a table nor a schema.\nParent's creation SQL:\n" + parent.getCreationSQL());
            return true;
        }
    }

    /**
     * Creates new extensions.
     * 
     * @param writer      writer the output should be written to
     * @param oldDatabase original database schema
     * @param newDatabase new database schema
     */
    
    private static void createNewExtensions(final PgDiffScript script, 
            final PgDatabase oldDatabase, final PgDatabase newDatabase) {
        for(final PgExtension newExt : newDatabase.getExtensions()) {
            if(oldDatabase.getExtension(newExt.getName()) == null) {
                writeCreationSql(script, null, newExt);
            }
        }
    }
    
    /**
     * Drops old extensions that do not exist anymore.
     *
     * @param writer      writer the output should be written to
     * @param oldDatabase original database schema
     * @param newDatabase new database schema
     */
    private static void dropOldExtensions(final PgDiffScript script, 
            final PgDatabase oldDatabase, final PgDatabase newDatabase) {
        for(final PgExtension oldExt : oldDatabase.getExtensions()) {
            if(newDatabase.getExtension(oldExt.getName()) == null) {
                writeDropSql(script, null, oldExt);
            }
        }
    }
    
    /**
     * Updates changed extensions.
     *
     * @param writer      writer the output should be written to
     * @param oldDatabase original database schema
     * @param newDatabase new database schema
     */
    private static void updateExtensions(final PgDiffScript script,
            final PgDatabase oldDatabase, final PgDatabase newDatabase) {
        for(final PgExtension newExt : newDatabase.getExtensions()) {
            final PgExtension oldExt = oldDatabase.getExtension(newExt.getName());
            if(oldExt == null) {
                continue;
            }
            
            if(!Objects.equals(newExt.getSchema(), oldExt.getSchema())) {
                script.addStatement("ALTER EXTENSION "
                        + PgDiffUtils.getQuotedName(oldExt.getName())
                        + " SET SCHEMA " + newExt.getSchema() + ";");
            }
        }
    }
    
    /**
     * Creates new schemas (not the objects inside the schemas).
     *
     * @param writer      writer the output should be written to
     * @param oldDatabase original database schema
     * @param newDatabase new database schema
     */
    private static void createNewSchemas(final PgDiffScript script,
            final PgDatabase oldDatabase, final PgDatabase newDatabase) {
        for (final PgSchema newSchema : newDatabase.getSchemas()) {
            if (oldDatabase.getSchema(newSchema.getName()) == null) {
                writeCreationSql(script, null, newSchema);
            }
        }
    }

    /**
     * Drops old schemas that do not exist anymore.
     *
     * @param writer      writer the output should be written to
     * @param arguments 
     * @param oldDatabase original database schema
     * @param newDatabase new database schema
     */
    private static void dropOldSchemas(final PgDiffScript script,
            PgDiffArguments arguments, final PgDatabase oldDatabase, final PgDatabase newDatabase) {
        for (final PgSchema oldSchema : oldDatabase.getSchemas()) {
            if (newDatabase.getSchema(oldSchema.getName()) == null) {
                if (!isFullSelection(oldSchema)){
                    script.addStatement("-- schema " + oldSchema.getName() + " was "
                            + "not dropped because it was not selected entirely");
                    continue;
                }
                // drop all contents of the schema
                PgSchema newSchema = new PgSchema(oldSchema.getName(),
                        "CREATE SCHEMA " + oldSchema.getName());
                updateSchemaContent(script,
                        depcyOld.getDb().getSchema(oldSchema.getName()), newSchema,
                        new SearchPathHelper(oldSchema.getName()), arguments);
                writeDropSql(script, null, oldSchema);
            }
        }
    }

    static boolean isFullSelection(PgStatement filtered) {
        PgDatabase fullDb = depcyOld.getDb();
        if (filtered instanceof PgSchema) {
            PgSchema filteredSchema = (PgSchema) filtered;
            PgSchema fullSchema = fullDb.getSchema(filteredSchema.getName());
            return fullSchema.equals(filteredSchema);
        } else if (filtered instanceof PgTable) {
            PgTable filteredTable = (PgTable) filtered;
            PgSchema fullSchema = fullDb.getSchema(filteredTable.getParent().getName()); 
            PgTable fullTable = fullSchema.getTable(filteredTable.getName());
            return fullTable.equals(filteredTable);
        }
        
        return true;
    }

    /**
     * Updates objects in schemas.
     *
     * @param writer      writer the output should be written to
     * @param arguments   object containing arguments settings
     * @param oldDatabase original database schema
     * @param newDatabase new database schema
     */
    private static void updateSchemas(final PgDiffScript script,
            final PgDiffArguments arguments, final PgDatabase oldDatabase,
            final PgDatabase newDatabase) {
        final boolean setSearchPath = newDatabase.getSchemas().size() > 1
                || !newDatabase.getSchemas().get(0).getName().equals("public");

        for (final PgSchema newSchema : newDatabase.getSchemas()) {
            final SearchPathHelper searchPathHelper =
                    new SearchPathHelper(newSchema.getName());
            if (!setSearchPath) {
                searchPathHelper.setWasOutput(true);
            }

            final PgSchema oldSchema = oldDatabase.getSchema(newSchema.getName());

            if (oldSchema != null) {

                if (!Objects.equals(oldSchema.getOwner(), newSchema.getOwner())) {
                    searchPathHelper.outputSearchPath(script);
                    script.addStatement(newSchema.getOwnerSQL());
                }
                
                if (!oldSchema.getPrivileges().equals(newSchema.getPrivileges())) {
                    script.addStatement(newSchema.getPrivilegesSQL());
                }
                
                if (oldSchema.getComment() == null
                        && newSchema.getComment() != null
                        || oldSchema.getComment() != null
                        && newSchema.getComment() != null
                        && !oldSchema.getComment().equals(
                                newSchema.getComment())) {
                    script.addStatement("COMMENT ON SCHEMA "
                            + PgDiffUtils.getQuotedName(newSchema.getName())
                            + " IS " + newSchema.getComment() + ';');
                } else if (oldSchema.getComment() != null
                        && newSchema.getComment() == null) {
                    script.addStatement("COMMENT ON SCHEMA "
                                + PgDiffUtils.getQuotedName(newSchema.getName())
                                + " IS NULL;");
                }
            }
            updateSchemaContent(script, oldSchema, newSchema, searchPathHelper, arguments);
        }
        
        // КОСТЫЛЬ
        // update contents of schema, if it is not present in new db and 
        // is partly selected by the user
        for (final PgSchema oldSchema : oldDatabase.getSchemas()) {
            if (newDatabase.getSchema(oldSchema.getName()) == null && !isFullSelection(oldSchema)){
                SearchPathHelper searchPath =
                        new SearchPathHelper(oldSchema.getName());
                PgSchema newSchema = new PgSchema(oldSchema.getName(), null);
                newSchema.setAuthorization(oldSchema.getAuthorization());
                newSchema.setComment(oldSchema.getComment());
                newSchema.setDefinition(oldSchema.getDefinition());
                
                updateSchemaContent(script, oldSchema, newSchema, searchPath, arguments);
            }
        }// КОСТЫЛЬ
    }

    private static void updateSchemaContent(PgDiffScript script, PgSchema oldSchema,
            PgSchema newSchema, SearchPathHelper searchPathHelper, PgDiffArguments arguments) {
        PgDiffTriggers.dropTriggers(
                script, oldSchema, newSchema, searchPathHelper);
        PgDiffFunctions.dropFunctions(
                script, oldSchema, newSchema, searchPathHelper);
        PgDiffViews.dropViews(
                script, oldSchema, newSchema, searchPathHelper);
        PgDiffConstraints.dropConstraints(
                script, oldSchema, newSchema, true, searchPathHelper);
        PgDiffConstraints.dropConstraints(
                script, oldSchema, newSchema, false, searchPathHelper);
        PgDiffIndexes.dropIndexes(
                script, oldSchema, newSchema, searchPathHelper);
        PgDiffTables.dropClusters(
                script, oldSchema, newSchema, searchPathHelper);
        PgDiffTables.dropTables(
                script, oldSchema, newSchema, searchPathHelper);
        PgDiffSequences.dropSequences(
                script, oldSchema, newSchema, searchPathHelper);

        PgDiffSequences.createSequences(
                script, oldSchema, newSchema, searchPathHelper);
        PgDiffSequences.alterSequences(
                script, arguments, oldSchema, newSchema, searchPathHelper);
        PgDiffTables.createTables(
                script, oldSchema, newSchema, searchPathHelper);
        PgDiffTables.alterTables(
                script, arguments, oldSchema, newSchema, searchPathHelper);
        PgDiffSequences.alterCreatedSequences(
                script, oldSchema, newSchema, searchPathHelper);
        PgDiffFunctions.createFunctions(
                script, arguments, oldSchema, newSchema, searchPathHelper);
        PgDiffConstraints.createConstraints(
                script, oldSchema, newSchema, true, searchPathHelper);
        PgDiffConstraints.createConstraints(
                script, oldSchema, newSchema, false, searchPathHelper);
        PgDiffIndexes.createIndexes(
                script, oldSchema, newSchema, searchPathHelper);
        PgDiffTables.createClusters(
                script, oldSchema, newSchema, searchPathHelper);
        PgDiffTriggers.createTriggers(
                script, oldSchema, newSchema, searchPathHelper);
        PgDiffViews.createViews(
                script, arguments, oldSchema, newSchema, searchPathHelper);
        PgDiffViews.alterViews(
                script, arguments, oldSchema, newSchema, searchPathHelper);

        PgDiffFunctions.alterComments(
                script, oldSchema, newSchema, searchPathHelper);
        PgDiffConstraints.alterComments(
                script, oldSchema, newSchema, searchPathHelper);
        PgDiffIndexes.alterComments(
                script, oldSchema, newSchema, searchPathHelper);
        PgDiffTriggers.alterComments(
                script, oldSchema, newSchema, searchPathHelper);
    }
    
    static void tempSwitchSearchPath(String switchTo, 
            final SearchPathHelper searchPathHelper, final PgDiffScript script){
        
        if (searchPathHelper.getWasOutput() == false ||
                !searchPathHelper.getSchemaName().equals(switchTo)){
            new SearchPathHelper(switchTo).outputSearchPath(script);
            
            searchPathHelper.setWasOutput(false);
        }
    }
    
    static void writeCreationSql(PgDiffScript script, String comment, PgStatement pgObject) {
        script.addCreate(pgObject, comment, pgObject.getCreationSQL());
    }
    
    static void writeDropSql(PgDiffScript script, String comment, PgStatement pgObject) {
        script.addDrop(pgObject, comment, pgObject.getDropSQL());
    }
    
    public static List<PgStatement> addUniqueDependenciesOnCreateEdit(PgDiffScript script,
            PgDiffArguments arguments, SearchPathHelper newSearchPathHelper, PgStatement fullStatement){
        
        List<PgStatement> specialDependencies = new ArrayList<PgStatement>(3);
        addUniqueDependencies(specialDependencies, script, arguments, newSearchPathHelper, fullStatement, null);
        return specialDependencies;
    }
    
    private static List<PgStatement> addUniqueDependencies(List<PgStatement> specialDependencies, 
            PgDiffScript script, PgDiffArguments arguments, SearchPathHelper searchPathHelper,
            PgStatement fullStatement, PgStatement previous){
        
        // order is insignificant, if we do firstLevelSearchOnly
        Set<PgStatement> dependencies = new HashSet<PgStatement>();
        getDependenciesSet(fullStatement, dependencies, true);
        
        for (PgStatement dep : dependencies){
            // специальный случай: так как ТАБЛИЦА <-> ПОСЛЕДОВАТЕЛЬНОСТЬ могут образовывать цикл и привести к StackOverflowError,
            // мы проверяем объект из предыдущей итерации на равество с зависимыми объектами текущего.
            // Если равенство выполняется, мы оказались в цикле и пропускаем текущую зависимость - прерываем цикл 
            if (dep instanceof PgTable && dep.equals(previous)){
                continue;
            }
            addUniqueDependencies(specialDependencies, script, arguments, searchPathHelper,
                    dep, fullStatement);
            if (dep instanceof PgView){
                PgView viewNew = (PgView)dep;
                String newSchemaName = viewNew.getParent().getName();
                PgSchema schemaOld = dbOld.getSchema(newSchemaName);
                
                PgView viewOld = (schemaOld == null) ? null : schemaOld.getView(viewNew.getName()); 
                if (viewOld == null){
                    tempSwitchSearchPath(newSchemaName, searchPathHelper, script);
                    writeCreationSql(script, "-- DEPCY: this view is in dependency tree of " + 
                    fullStatement.getBareName(), viewNew);
                }else if (viewOld != null && !viewOld.equals(viewNew)){
                    tempSwitchSearchPath(newSchemaName, searchPathHelper, script);
                    writeDropSql(script, "-- DEPCY: recreating view that is in dependency "
                            + "tree of " + fullStatement.getBareName(), viewNew);
                    writeCreationSql(script, "-- DEPCY: recreating view that is in "
                            + "dependency tree of " + fullStatement.getBareName(),  viewNew);
                }
            }else if (dep instanceof PgSequence){
                if (    fullStatement instanceof PgSequence && 
                        dep.getName().equals(fullStatement.getName()) && 
                        dep.getParent().getName().equals(fullStatement.getParent().getName())){
                    continue;
                }
                
                if(specialDependencies.contains(dep)){
                    continue;
                }
                PgSequence sequenceNew = (PgSequence)dep;
                String newSchemaName = sequenceNew.getParent().getName();
                
                PgSchema schemaOld = dbOld.getSchema(newSchemaName);
                if (schemaOld == null || schemaOld.getSequence(sequenceNew.getName()) == null){
                    tempSwitchSearchPath(newSchemaName, searchPathHelper, script);
                    writeCreationSql(script, "-- DEPCY: this sequence is in dependency tree of " + 
                            fullStatement.getBareName(), sequenceNew);
                    specialDependencies.add(sequenceNew);
                }
            }else if (dep instanceof PgTable){
                if (    fullStatement instanceof PgTable && 
                        dep.getName().equals(fullStatement.getName()) && 
                        dep.getParent().getName().equals(fullStatement.getParent().getName())){
                    continue;
                }
                PgTable tableNew = (PgTable)dep;
                String newSchemaName = tableNew.getParent().getName();
                PgSchema schemaOld = dbOld.getSchema(newSchemaName);

                PgTable tableOld = (schemaOld == null) ? null : schemaOld.getTable(tableNew.getName());
                if (tableOld == null){
                    tempSwitchSearchPath(newSchemaName, searchPathHelper, script);
                    writeCreationSql(script, "-- DEPCY: this table is in dependency tree of " + 
                            fullStatement.getBareName(), tableNew);
                }else if (fullStatement instanceof PgView && !tableNew.equals(tableOld)){
                    // special case: modified table is required for view creation/edit
                    // ensure that dependant view is in NEW/EDIT state
                    PgView viewNew = (PgView) fullStatement;
                    PgSchema viewSchemaOld = dbOld.getSchema(viewNew.getParent().getName());
                    if (viewSchemaOld == null || !viewNew.equals(viewSchemaOld.getView(viewNew.getName()))){
                        for(GenericColumn newColumn : viewNew.getSelect().getColumns()){
                            if (    tableOld.getParent().getName().equals(newColumn.schema) && 
                                    tableOld.getName().equals(newColumn.table) && 
                                    tableOld.getColumn(newColumn.column) == null){
                                script.addStatement("-- DEPCY: this table is in dependency tree of " + 
                                    fullStatement.getBareName());
                                PgDiffTables.alterTable(script, arguments,
                                        tableOld, tableNew, searchPathHelper);
                                break;
                            }
                        }
                    }
                }// end special case
            }else if (dep instanceof PgSchema){
                PgSchema schemaNew = (PgSchema) dep;
                
                // NB public schema always exists in dbOld, thus it will 
                // never be created here
                if (dbOld.getSchema(schemaNew.getName()) == null){
                    writeCreationSql(script, "-- DEPCY: this schema is in dependency tree of " + 
                            fullStatement.getBareName(), schemaNew);
                }
            }
        }

        return specialDependencies;
    }
    
    public static PgDatabase getFullDbNew() {
        return dbNew;
    }

    private PgDiff() {
    }
}
