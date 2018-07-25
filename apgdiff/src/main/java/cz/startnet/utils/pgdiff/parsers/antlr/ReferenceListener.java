package cz.startnet.utils.pgdiff.parsers.antlr;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.eclipse.core.runtime.IProgressMonitor;

import cz.startnet.utils.pgdiff.PgDiffUtils;
import cz.startnet.utils.pgdiff.parsers.antlr.SQLParser.Alter_domain_statementContext;
import cz.startnet.utils.pgdiff.parsers.antlr.SQLParser.Alter_fts_configurationContext;
import cz.startnet.utils.pgdiff.parsers.antlr.SQLParser.Alter_fts_statementContext;
import cz.startnet.utils.pgdiff.parsers.antlr.SQLParser.Alter_function_statementContext;
import cz.startnet.utils.pgdiff.parsers.antlr.SQLParser.Alter_schema_statementContext;
import cz.startnet.utils.pgdiff.parsers.antlr.SQLParser.Alter_sequence_statementContext;
import cz.startnet.utils.pgdiff.parsers.antlr.SQLParser.Alter_table_statementContext;
import cz.startnet.utils.pgdiff.parsers.antlr.SQLParser.Alter_type_statementContext;
import cz.startnet.utils.pgdiff.parsers.antlr.SQLParser.Alter_view_statementContext;
import cz.startnet.utils.pgdiff.parsers.antlr.SQLParser.Comment_on_statementContext;
import cz.startnet.utils.pgdiff.parsers.antlr.SQLParser.Constraint_commonContext;
import cz.startnet.utils.pgdiff.parsers.antlr.SQLParser.Create_domain_statementContext;
import cz.startnet.utils.pgdiff.parsers.antlr.SQLParser.Create_extension_statementContext;
import cz.startnet.utils.pgdiff.parsers.antlr.SQLParser.Create_fts_configurationContext;
import cz.startnet.utils.pgdiff.parsers.antlr.SQLParser.Create_fts_dictionaryContext;
import cz.startnet.utils.pgdiff.parsers.antlr.SQLParser.Create_fts_parserContext;
import cz.startnet.utils.pgdiff.parsers.antlr.SQLParser.Create_fts_templateContext;
import cz.startnet.utils.pgdiff.parsers.antlr.SQLParser.Create_function_statementContext;
import cz.startnet.utils.pgdiff.parsers.antlr.SQLParser.Create_index_statementContext;
import cz.startnet.utils.pgdiff.parsers.antlr.SQLParser.Create_rewrite_statementContext;
import cz.startnet.utils.pgdiff.parsers.antlr.SQLParser.Create_schema_statementContext;
import cz.startnet.utils.pgdiff.parsers.antlr.SQLParser.Create_sequence_statementContext;
import cz.startnet.utils.pgdiff.parsers.antlr.SQLParser.Create_table_statementContext;
import cz.startnet.utils.pgdiff.parsers.antlr.SQLParser.Create_trigger_statementContext;
import cz.startnet.utils.pgdiff.parsers.antlr.SQLParser.Create_type_statementContext;
import cz.startnet.utils.pgdiff.parsers.antlr.SQLParser.Create_view_statementContext;
import cz.startnet.utils.pgdiff.parsers.antlr.SQLParser.Define_columnsContext;
import cz.startnet.utils.pgdiff.parsers.antlr.SQLParser.Define_typeContext;
import cz.startnet.utils.pgdiff.parsers.antlr.SQLParser.Drop_function_statementContext;
import cz.startnet.utils.pgdiff.parsers.antlr.SQLParser.Drop_rule_statementContext;
import cz.startnet.utils.pgdiff.parsers.antlr.SQLParser.Drop_statementsContext;
import cz.startnet.utils.pgdiff.parsers.antlr.SQLParser.Drop_trigger_statementContext;
import cz.startnet.utils.pgdiff.parsers.antlr.SQLParser.Function_parametersContext;
import cz.startnet.utils.pgdiff.parsers.antlr.SQLParser.IdentifierContext;
import cz.startnet.utils.pgdiff.parsers.antlr.SQLParser.Rule_commonContext;
import cz.startnet.utils.pgdiff.parsers.antlr.SQLParser.Schema_qualified_nameContext;
import cz.startnet.utils.pgdiff.parsers.antlr.SQLParser.Schema_qualified_name_nontypeContext;
import cz.startnet.utils.pgdiff.parsers.antlr.SQLParser.Select_stmtContext;
import cz.startnet.utils.pgdiff.parsers.antlr.SQLParser.Sequence_bodyContext;
import cz.startnet.utils.pgdiff.parsers.antlr.SQLParser.Set_statementContext;
import cz.startnet.utils.pgdiff.parsers.antlr.SQLParser.Set_statement_valueContext;
import cz.startnet.utils.pgdiff.parsers.antlr.SQLParser.Table_actionContext;
import cz.startnet.utils.pgdiff.parsers.antlr.SQLParser.Table_column_defContext;
import cz.startnet.utils.pgdiff.parsers.antlr.SQLParser.Table_of_type_column_defContext;
import cz.startnet.utils.pgdiff.parsers.antlr.SQLParser.Table_referencesContext;
import cz.startnet.utils.pgdiff.parsers.antlr.exception.MonitorCancelledRuntimeException;
import cz.startnet.utils.pgdiff.parsers.antlr.statements.ParserAbstract;
import cz.startnet.utils.pgdiff.schema.PgDatabase;
import cz.startnet.utils.pgdiff.schema.PgObjLocation;
import cz.startnet.utils.pgdiff.schema.StatementActions;
import ru.taximaxim.codekeeper.apgdiff.ApgdiffConsts;
import ru.taximaxim.codekeeper.apgdiff.Log;
import ru.taximaxim.codekeeper.apgdiff.model.difftree.DbObjType;

/**
 * This class only fills 2 lists Definition and Reference for objects in file
 * @author botov_av
 *
 */
public class ReferenceListener extends SQLParserBaseListener {

    private String defSchema = ApgdiffConsts.PUBLIC;
    private final String filePath;
    private final Map<String, List<PgObjLocation>> definitions;
    private final Map<String, List<PgObjLocation>> references;
    private final List<StatementBodyContainer> statementBodies = new ArrayList<>();
    private final IProgressMonitor monitor;

    public ReferenceListener(PgDatabase db, String filePath, IProgressMonitor monitor) {
        this.definitions = db.getObjDefinitions();
        this.references = db.getObjReferences();
        this.monitor = monitor;
        this.filePath = filePath;
    }

    private void safeParseStatement(Runnable r) {
        try {
            PgDiffUtils.checkCancelled(monitor);
            r.run();
        } catch (InterruptedException ex) {
            throw new MonitorCancelledRuntimeException();
        } catch (Exception e) {
            Log.log(Log.LOG_ERROR, e.getLocalizedMessage());
        }
    }

    @Override
    public void exitCreate_table_statement(Create_table_statementContext ctx) {
        safeParseStatement(() -> createTable(ctx));
    }

    @Override
    public void exitCreate_index_statement(Create_index_statementContext ctx) {
        safeParseStatement(() -> createIndex(ctx));
    }

    @Override
    public void exitCreate_extension_statement(Create_extension_statementContext ctx) {
        safeParseStatement(() -> createExtension(ctx));
    }

    @Override
    public void exitCreate_trigger_statement(Create_trigger_statementContext ctx) {
        safeParseStatement(() -> createTrigger(ctx));
    }

    @Override
    public void exitCreate_domain_statement(Create_domain_statementContext ctx) {
        safeParseStatement(() -> createDomain(ctx));
    }

    @Override
    public void exitCreate_type_statement(Create_type_statementContext ctx) {
        safeParseStatement(() -> createType(ctx));
    }

    @Override
    public void exitCreate_rewrite_statement(Create_rewrite_statementContext ctx) {
        safeParseStatement(() -> createRewrite(ctx));
    }

    @Override
    public void exitCreate_function_statement(Create_function_statementContext ctx) {
        safeParseStatement(() -> createFunction(ctx));
    }

    @Override
    public void exitCreate_sequence_statement(Create_sequence_statementContext ctx) {
        safeParseStatement(() -> createSequence(ctx));
    }

    @Override
    public void exitCreate_schema_statement(Create_schema_statementContext ctx) {
        safeParseStatement(() -> createSchema(ctx));
    }

    @Override
    public void exitCreate_view_statement(Create_view_statementContext ctx) {
        safeParseStatement(() -> createView(ctx));
    }

    @Override
    public void exitCreate_fts_parser(Create_fts_parserContext ctx) {
        safeParseStatement(() -> createFtsParser(ctx));
    }

    @Override
    public void exitCreate_fts_template(Create_fts_templateContext ctx) {
        safeParseStatement(() -> createFtsTemplate(ctx));
    }

    @Override
    public void exitCreate_fts_dictionary(Create_fts_dictionaryContext ctx) {
        safeParseStatement(() -> createFtsDictionary(ctx));
    }

    @Override
    public void exitCreate_fts_configuration(Create_fts_configurationContext ctx) {
        safeParseStatement(() -> createFtsConfiguration(ctx));
    }

    @Override
    public void exitComment_on_statement(Comment_on_statementContext ctx) {
        safeParseStatement(() -> commentOn(ctx));
    }

    @Override
    public void exitSet_statement(Set_statementContext ctx) {
        safeParseStatement(() -> createSet(ctx));
    }

    @Override
    public void exitRule_common(Rule_commonContext ctx) {
        safeParseStatement(() -> createRule(ctx));
    }

    @Override
    public void exitAlter_function_statement(Alter_function_statementContext ctx) {
        safeParseStatement(() -> alterFunction(ctx));
    }

    @Override
    public void exitAlter_schema_statement(Alter_schema_statementContext ctx) {
        safeParseStatement(() -> alterSchema(ctx));
    }

    @Override
    public void exitAlter_table_statement(Alter_table_statementContext ctx) {
        safeParseStatement(() -> alterTable(ctx));
    }

    @Override
    public void exitAlter_sequence_statement(Alter_sequence_statementContext ctx) {
        safeParseStatement(() -> alterSequence(ctx));
    }

    @Override
    public void exitAlter_view_statement(Alter_view_statementContext ctx) {
        safeParseStatement(() -> alterView(ctx));
    }

    @Override
    public void exitAlter_domain_statement(Alter_domain_statementContext ctx) {
        safeParseStatement(() -> alterDomain(ctx));
    }

    @Override
    public void exitAlter_type_statement(Alter_type_statementContext ctx) {
        safeParseStatement(() -> alterType(ctx));
    }

    @Override
    public void exitAlter_fts_statement(Alter_fts_statementContext ctx) {
        safeParseStatement(() -> alterFts(ctx));
    }

    @Override
    public void exitDrop_statements(Drop_statementsContext ctx) {
        safeParseStatement(() -> drop(ctx));
    }

    @Override
    public void exitDrop_trigger_statement(Drop_trigger_statementContext ctx) {
        safeParseStatement(() -> dropTrigger(ctx));
    }

    @Override
    public void exitDrop_rule_statement(Drop_rule_statementContext ctx) {
        safeParseStatement(() -> dropRule(ctx));
    }

    @Override
    public void exitDrop_function_statement(Drop_function_statementContext ctx) {
        safeParseStatement(() -> dropFunction(ctx));
    }

    private String getDefSchemaName() {
        return defSchema;
    }

    private void addReferenceOnSchema(List<IdentifierContext> ids, String schemaName,
            ParserRuleContext ctx) {
        if (schemaName != null) {
            Token startSchemaToken = QNameParser.getSchemaNameCtx(ids).getStart();
            addObjReference(null, schemaName,
                    DbObjType.SCHEMA, StatementActions.NONE,
                    startSchemaToken.getStartIndex(), startSchemaToken.getLine(),
                    ParserAbstract.getFullCtxText(ctx.getParent()));
        }
    }

    private void addFullObjReference(String schemaName, String name,
            ParserRuleContext ctx, DbObjType type, StatementActions action,
            ParserRuleContext def) {
        int offset = 0;
        if (schemaName != null) {
            offset = schemaName.length() + 1;
            addObjReference(null, schemaName, DbObjType.SCHEMA, StatementActions.NONE,
                    ctx.getStart().getStartIndex(), ctx.getStart().getLine(),
                    ParserAbstract.getFullCtxText(def));
        }

        addObjReference(schemaName, name, type, action,
                ctx.getStart().getStartIndex() + offset, ctx.getStart().getLine(),
                ParserAbstract.getFullCtxText(def));
    }

    public void createTable(Create_table_statementContext ctx){
        List<IdentifierContext> ids = ctx.name.identifier();
        String schemaName = QNameParser.getSchemaName(ids, getDefSchemaName());
        addReferenceOnSchema(ids, schemaName, ctx);
        Define_columnsContext defintColumns = ctx.define_table().define_columns();
        Define_typeContext defineType = ctx.define_table().define_type();

        if (defintColumns != null) {
            for (Table_column_defContext colCtx : defintColumns.table_col_def) {
                if (colCtx.tabl_constraint != null) {
                    getTableConstraint(colCtx.tabl_constraint);
                }
            }
        }

        if (defineType != null && defineType.list_of_type_column_def() != null) {
            for (Table_of_type_column_defContext typeCtx : defineType.list_of_type_column_def().table_col_def) {
                if (typeCtx.tabl_constraint != null) {
                    getTableConstraint(typeCtx.tabl_constraint);
                }
            }
        }

        fillObjDefinition(schemaName, QNameParser.getFirstNameCtx(ids), DbObjType.TABLE);
    }

    public void createIndex(Create_index_statementContext ctx){
        List<IdentifierContext> ids = ctx.table_name.identifier();
        String schemaName = QNameParser.getSchemaName(ids, getDefSchemaName());
        addFullObjReference(schemaName, QNameParser.getFirstName(ids), ctx.table_name,
                DbObjType.TABLE, StatementActions.NONE, ctx.getParent());
        if (ctx.name != null) {
            fillObjDefinition(schemaName, ctx.name, DbObjType.INDEX);
        }
    }

    public void createExtension(Create_extension_statementContext ctx) {
        if (ctx.schema_with_name() != null) {
            addObjReference(null,
                    ctx.schema_with_name().name.getText(),
                    DbObjType.SCHEMA, StatementActions.NONE,
                    ctx.schema_with_name().name.getStart().getStartIndex(), ctx.schema_with_name().name.getStart().getLine(),
                    ParserAbstract.getFullCtxText(ctx.getParent()));
        }
        fillObjDefinition(null, ctx.name, DbObjType.EXTENSION);
    }

    public void createTrigger(Create_trigger_statementContext ctx) {
        List<IdentifierContext> ids = ctx.table_name.identifier();
        addFullObjReference(QNameParser.getSchemaName(ids, getDefSchemaName()),
                QNameParser.getFirstName(ids), ctx.table_name, DbObjType.TABLE,
                StatementActions.NONE, ctx.getParent());

        Schema_qualified_name_nontypeContext funcNameCtx = ctx.func_name.function_name()
                .schema_qualified_name_nontype();
        IdentifierContext sch = funcNameCtx.schema;
        String funcSchema = sch != null ?  sch.getText() : getDefSchemaName();
        String funcName = funcNameCtx.identifier_nontype().getText();
        addFullObjReference(funcSchema, funcName, ctx.func_name,
                DbObjType.FUNCTION, StatementActions.NONE, ctx.getParent());
        fillObjDefinition(null, ctx.name, DbObjType.TRIGGER);
    }

    public void createDomain(Create_domain_statementContext ctx) {
        List<IdentifierContext> ids = ctx.name.identifier();
        String schemaName = QNameParser.getSchemaName(ids, getDefSchemaName());
        addReferenceOnSchema(ids, schemaName, ctx);
        addObjReference(schemaName, ParserAbstract.getFullCtxText(ctx.dat_type),
                DbObjType.TYPE, StatementActions.NONE,
                ctx.dat_type.getStart().getStartIndex(), ctx.dat_type.getStart().getLine(),
                ParserAbstract.getFullCtxText(ctx.getParent()));
        fillObjDefinition(schemaName, QNameParser.getFirstNameCtx(ids), DbObjType.DOMAIN);
    }

    public void createType(Create_type_statementContext ctx) {
        List<IdentifierContext> ids = ctx.name.identifier();
        String schemaName = QNameParser.getSchemaName(ids, getDefSchemaName());
        addReferenceOnSchema(ids, schemaName, ctx);
        fillObjDefinition(schemaName, QNameParser.getFirstNameCtx(ids), DbObjType.TYPE);
    }

    public void createRewrite(Create_rewrite_statementContext ctx) {
        List<IdentifierContext> ids = ctx.table_name.identifier();
        String schemaName = QNameParser.getSchemaName(ids, getDefSchemaName());
        addFullObjReference(schemaName, QNameParser.getFirstName(ids), ctx.table_name,
                DbObjType.TABLE, StatementActions.NONE, ctx.getParent());
        // TODO process references in statements/expressions
        fillObjDefinition(null, ctx.name, DbObjType.RULE);
    }

    public void createFunction(Create_function_statementContext ctx) {
        List<IdentifierContext> ids = ctx.function_parameters().name.identifier();
        String schemaName = QNameParser.getSchemaName(ids, getDefSchemaName());
        addReferenceOnSchema(ids, schemaName, ctx);
        statementBodies.add(new StatementBodyContainer(filePath, ctx.funct_body));
        fillObjDefinition(schemaName, QNameParser.getFirstNameCtx(ids), DbObjType.FUNCTION);
    }

    public void createSequence(Create_sequence_statementContext ctx) {
        List<IdentifierContext> ids = ctx.name.identifier();
        String schemaName = QNameParser.getSchemaName(ids, getDefSchemaName());
        addReferenceOnSchema(ids, schemaName, ctx);
        fillObjDefinition(schemaName, QNameParser.getFirstNameCtx(ids), DbObjType.SEQUENCE);
    }

    public void createSchema(Create_schema_statementContext ctx) {
        if (ctx.name != null) {
            fillObjDefinition(null, ctx.name, DbObjType.SCHEMA);
        }
    }

    public void createView(Create_view_statementContext ctx) {
        List<IdentifierContext> ids = ctx.name.identifier();
        String schemaName = QNameParser.getSchemaName(ids, getDefSchemaName());
        addReferenceOnSchema(ids, schemaName, ctx);

        Select_stmtContext select = ctx.v_query;
        if (select != null) {
            statementBodies.add(new StatementBodyContainer(filePath, select));
        }

        fillObjDefinition(schemaName, QNameParser.getFirstNameCtx(ids), DbObjType.VIEW);
    }

    private void createFtsParser(Create_fts_parserContext ctx) {
        List<IdentifierContext> ids = ctx.name.identifier();
        String schemaName = QNameParser.getSchemaName(ids, getDefSchemaName());
        addReferenceOnSchema(ids, schemaName, ctx);
        fillObjDefinition(schemaName, QNameParser.getFirstNameCtx(ids), DbObjType.FTS_PARSER);
    }

    private void createFtsTemplate(Create_fts_templateContext ctx) {
        List<IdentifierContext> ids = ctx.name.identifier();
        String schemaName = QNameParser.getSchemaName(ids, getDefSchemaName());
        addReferenceOnSchema(ids, schemaName, ctx);
        fillObjDefinition(schemaName, QNameParser.getFirstNameCtx(ids), DbObjType.FTS_TEMPLATE);
    }

    private void createFtsDictionary(Create_fts_dictionaryContext ctx) {
        List<IdentifierContext> templateIds = ctx.template.identifier();
        addFullObjReference(QNameParser.getSchemaName(templateIds, "pg_catalog"),
                QNameParser.getFirstName(templateIds), ctx.template,
                DbObjType.FTS_TEMPLATE, StatementActions.NONE, ctx.getParent());
        List<IdentifierContext> ids = ctx.name.identifier();
        String schemaName = QNameParser.getSchemaName(ids, getDefSchemaName());
        addReferenceOnSchema(ids, schemaName, ctx);
        fillObjDefinition(schemaName, QNameParser.getFirstNameCtx(ids), DbObjType.FTS_DICTIONARY);
    }

    private void createFtsConfiguration(Create_fts_configurationContext ctx) {
        List<IdentifierContext> parserIds = ctx.parser_name.identifier();
        addFullObjReference(QNameParser.getSchemaName(parserIds, "pg_catalog"),
                QNameParser.getFirstName(parserIds), ctx.parser_name,
                DbObjType.FTS_PARSER, StatementActions.NONE, ctx.getParent());

        List<IdentifierContext> ids = ctx.name.identifier();
        String schemaName = QNameParser.getSchemaName(ids, getDefSchemaName());
        addReferenceOnSchema(ids, schemaName, ctx);
        fillObjDefinition(schemaName, QNameParser.getFirstNameCtx(ids), DbObjType.FTS_CONFIGURATION);
    }

    public void commentOn(Comment_on_statementContext ctx) {
        if (ctx.name == null) {
            return;
        }
        List<IdentifierContext> ids = ctx.name.identifier();
        String name = QNameParser.getFirstName(ids);
        String schemaName = QNameParser.getSchemaName(ids, getDefSchemaName());
        String comment = "";
        if (ctx.comment_text != null) {
            comment = ctx.comment_text.getText();
        }
        DbObjType type = null;
        if (ctx.FUNCTION() != null) {
            type = DbObjType.FUNCTION;
        } else if (ctx.COLUMN() != null) {
            String tableName = QNameParser.getSecondName(ids);
            if (schemaName.equals(tableName)) {
                schemaName = getDefSchemaName();
            }
            // TODO need to correct links for 'VIEW-column' comments.
            // example: COMMENT ON COLUMN public.view_name.ts IS 'Comment for column in query of VIEW';
            addFullObjReference(schemaName, tableName, ctx.name,
                    DbObjType.TABLE, StatementActions.COMMENT, ctx.getParent());
        } else if (ctx.EXTENSION() != null) {
            schemaName = null;
            type = DbObjType.EXTENSION;
        } else if (ctx.TRIGGER() != null) {
            List<IdentifierContext> idsTblTrigger = ctx.table_name.identifier();
            addFullObjReference(QNameParser.getSchemaName(idsTblTrigger, getDefSchemaName()),
                    QNameParser.getFirstName(idsTblTrigger), ctx.table_name,
                    DbObjType.TABLE, StatementActions.NONE, ctx.getParent());
            schemaName = null;
            type = DbObjType.TRIGGER;
        } else if (ctx.RULE() != null) {
            List<IdentifierContext> idsTblRule = ctx.table_name.identifier();
            addFullObjReference(QNameParser.getSchemaName(idsTblRule, getDefSchemaName()),
                    QNameParser.getFirstName(idsTblRule), ctx.table_name,
                    DbObjType.TABLE, StatementActions.NONE, ctx.getParent());
            schemaName = null;
            type = DbObjType.RULE;
        } else if (ctx.CONSTRAINT() != null) {
            List<IdentifierContext> idsTblConstr = ctx.table_name.identifier();
            addFullObjReference(QNameParser.getSchemaName(idsTblConstr, getDefSchemaName()),
                    QNameParser.getFirstName(idsTblConstr), ctx.table_name,
                    DbObjType.TABLE, StatementActions.NONE, ctx.getParent());
            schemaName = null;
            type = DbObjType.CONSTRAINT;
        } else if (ctx.INDEX() != null) {
            type = DbObjType.INDEX;
        } else if (ctx.SCHEMA() != null) {
            schemaName = null;
            type = DbObjType.SCHEMA;
        } else if (ctx.SEQUENCE() != null) {
            type = DbObjType.SEQUENCE;
        } else if (ctx.TABLE() != null) {
            type = DbObjType.TABLE;
        } else if (ctx.VIEW() != null) {
            type = DbObjType.VIEW;
        } else if (ctx.DOMAIN() != null) {
            type = DbObjType.DOMAIN;
        } else if (ctx.PARSER() != null) {
            type = DbObjType.FTS_PARSER;
        } else if (ctx.TEMPLATE() != null) {
            type = DbObjType.FTS_TEMPLATE;
        } else if (ctx.DICTIONARY() != null) {
            type = DbObjType.FTS_DICTIONARY;
        } else if (ctx.CONFIGURATION() != null) {
            type = DbObjType.FTS_CONFIGURATION;
        }

        if (type != null) {
            addFullObjReference(schemaName, name, ctx.name, type, StatementActions.COMMENT, ctx.getParent());
            setCommentToDefinition(name, type, comment);
        }
    }

    public void createSet(Set_statementContext ctx) {
        if (ctx.config_param != null && "search_path".equalsIgnoreCase(ctx.config_param.getText())) {
            for (Set_statement_valueContext value : ctx.config_param_val) {
                addObjReference(null, value.getText(),
                        DbObjType.SCHEMA, StatementActions.NONE,
                        value.getStart().getStartIndex(), value.getStart().getLine(),
                        ParserAbstract.getFullCtxText(ctx.getParent()));
                defSchema = value.getText();
                break;
            }
        }
    }

    public void createRule(Rule_commonContext ctx) {
        DbObjType type = null;
        List<Schema_qualified_nameContext> obj_name = new ArrayList<>();
        if (ctx.body_rule.body_rules_rest().obj_name != null) {
            obj_name = ctx.body_rule.body_rules_rest().obj_name.name;
        } else if (ctx.body_rule.on_table() != null) {
            type = DbObjType.TABLE;
            obj_name = ctx.body_rule.on_table().obj_name.name;
        } else if (ctx.body_rule.on_sequence() != null) {
            type = DbObjType.SEQUENCE;
            obj_name = ctx.body_rule.on_sequence().obj_name.name;
        } else if (ctx.body_rule.on_database() != null) {
            type = DbObjType.DATABASE;
            obj_name = ctx.body_rule.on_database().obj_name.name;
        } else if (ctx.body_rule.on_datawrapper_server_lang() != null) {
            obj_name = ctx.body_rule.on_datawrapper_server_lang().obj_name.name;
        } else if (ctx.body_rule.on_function() != null) {
            type = DbObjType.FUNCTION;
            for (Function_parametersContext functparam : ctx.body_rule.on_function().obj_name) {
                List<IdentifierContext> functparamIds = functparam.name.identifier();
                addFullObjReference(getDefSchemaName(), QNameParser.getFirstName(functparamIds),
                        functparam.name, DbObjType.FUNCTION, StatementActions.NONE, ctx.getParent());
            }
        } else if (ctx.body_rule.on_large_object() != null) {
            obj_name = ctx.body_rule.on_large_object().obj_name.name;
        } else if (ctx.body_rule.on_schema() != null) {
            type = DbObjType.SCHEMA;
            obj_name = ctx.body_rule.on_schema().obj_name.name;
        } else if (ctx.body_rule.on_tablespace() != null) {
            obj_name = ctx.body_rule.on_tablespace().obj_name.name;
        }

        for (Schema_qualified_nameContext name : obj_name) {
            addToDB(name, type, ctx);
        }
    }

    private void addToDB(Schema_qualified_nameContext name, DbObjType type, Rule_commonContext ctx) {
        if (type == null) {
            return;
        }
        List<IdentifierContext> ids = name.identifier();
        String schemaName = QNameParser.getSchemaName(ids, getDefSchemaName());
        if (DbObjType.SCHEMA == type) {
            schemaName = null;
        }
        addFullObjReference(schemaName, QNameParser.getFirstName(ids), name, type,
                StatementActions.NONE, ctx.getParent());
    }

    public void alterFunction(Alter_function_statementContext ctx) {
        List<IdentifierContext> ids = ctx.function_parameters().name.identifier();
        addFullObjReference(QNameParser.getSchemaName(ids, getDefSchemaName()),
                QNameParser.getFirstName(ids), ctx.function_parameters().name,
                DbObjType.FUNCTION, StatementActions.ALTER, ctx.getParent());
    }

    public void alterSchema(Alter_schema_statementContext ctx) {
        addObjReference(null, ctx.schema_with_name().name.getText(),
                DbObjType.SCHEMA, StatementActions.ALTER,
                ctx.schema_with_name().name.getStart().getStartIndex(), ctx.schema_with_name().name.getStart().getLine(),
                ParserAbstract.getFullCtxText(ctx.getParent()));
    }

    public void alterTable(Alter_table_statementContext ctx) {
        List<IdentifierContext> ids = ctx.name.identifier();
        String name = QNameParser.getFirstName(ids);
        String schemaName = QNameParser.getSchemaName(ids, getDefSchemaName());
        for (Table_actionContext tablAction : ctx.table_action()) {
            addFullObjReference(schemaName, name, ctx.name, DbObjType.TABLE,
                    StatementActions.ALTER, ctx.getParent());
            if (tablAction.tabl_constraint != null) {
                getTableConstraint(tablAction.tabl_constraint);
            }
        }
    }

    public void alterSequence(Alter_sequence_statementContext ctx) {
        List<IdentifierContext> ids = ctx.name.identifier();
        String schemaName = QNameParser.getSchemaName(ids, getDefSchemaName());
        for (Sequence_bodyContext seqbody : ctx.sequence_body()) {
            if (seqbody.OWNED() != null && seqbody.col_name != null) {
                List<IdentifierContext> idsColname = seqbody.col_name.identifier();
                String tableName = QNameParser.getSecondName(idsColname);
                String schName = QNameParser.getSchemaName(idsColname, getDefSchemaName());
                if (tableName.equals(schName)) {
                    schName = schemaName;
                }
                addFullObjReference(schName, tableName, seqbody.col_name,
                        DbObjType.TABLE, StatementActions.NONE, ctx.getParent());
            }
        }

        addFullObjReference(schemaName, QNameParser.getFirstName(ids), ctx.name,
                DbObjType.SEQUENCE, StatementActions.ALTER, ctx.getParent());
    }

    public void alterView(Alter_view_statementContext ctx) {
        List<IdentifierContext> ids = ctx.name.identifier();
        addFullObjReference(QNameParser.getSchemaName(ids, getDefSchemaName()),
                QNameParser.getFirstName(ids), ctx.name,
                DbObjType.VIEW, StatementActions.ALTER, ctx.getParent());
    }

    public void alterDomain(Alter_domain_statementContext ctx) {
        List<IdentifierContext> ids = ctx.name.identifier();
        addFullObjReference(QNameParser.getSchemaName(ids, getDefSchemaName()),
                QNameParser.getFirstName(ids), ctx.name,
                DbObjType.DOMAIN, StatementActions.ALTER, ctx.getParent());
    }

    public void alterType(Alter_type_statementContext ctx) {
        List<IdentifierContext> ids = ctx.name.identifier();
        addFullObjReference(QNameParser.getSchemaName(ids, getDefSchemaName()),
                QNameParser.getFirstName(ids), ctx.name,
                DbObjType.TYPE, StatementActions.ALTER, ctx.getParent());
    }

    private void alterFts(Alter_fts_statementContext ctx) {
        DbObjType type;
        if (ctx.DICTIONARY() != null) {
            type = DbObjType.FTS_DICTIONARY;
        } else if (ctx.TEMPLATE() != null) {
            type = DbObjType.FTS_TEMPLATE;
        } else if (ctx.PARSER() != null) {
            type = DbObjType.FTS_PARSER;
        } else {
            type = DbObjType.FTS_CONFIGURATION;
        }

        List<IdentifierContext> ids = ctx.name.identifier();
        addFullObjReference(QNameParser.getSchemaName(ids, getDefSchemaName()),
                QNameParser.getFirstName(ids), ctx.name,
                type, StatementActions.ALTER, ctx.getParent());

        Alter_fts_configurationContext afc = ctx.alter_fts_configuration();

        if (afc != null && afc.dictionaries != null) {
            for (Schema_qualified_nameContext objName : afc.dictionaries) {
                List<IdentifierContext> dictIds = objName.identifier();
                addFullObjReference(QNameParser.getSchemaName(dictIds, getDefSchemaName()),
                        QNameParser.getFirstName(dictIds), objName,
                        DbObjType.FTS_DICTIONARY, StatementActions.NONE, ctx.getParent());
            }
        }
    }

    public void drop(Drop_statementsContext ctx) {
        DbObjType type = null;
        if (ctx.DATABASE()!= null) {
            type = DbObjType.DATABASE;
        } else if (ctx.TABLE() != null) {
            type = DbObjType.TABLE;
        } else if (ctx.EXTENSION() != null) {
            type = DbObjType.EXTENSION;
        } else if (ctx.SCHEMA() != null) {
            type = DbObjType.SCHEMA;
        } else if (ctx.SEQUENCE() != null) {
            type = DbObjType.SEQUENCE;
        } else if (ctx.VIEW() != null) {
            type = DbObjType.VIEW;
        } else if (ctx.INDEX() != null) {
            type = DbObjType.INDEX;
        } else if (ctx.DOMAIN() != null) {
            type = DbObjType.DOMAIN;
        } else if (ctx.TYPE() != null) {
            type = DbObjType.TYPE;
        } else if (ctx.DICTIONARY() != null) {
            type = DbObjType.FTS_DICTIONARY;
        } else if (ctx.TEMPLATE() != null) {
            type = DbObjType.FTS_TEMPLATE;
        } else if (ctx.PARSER() != null) {
            type = DbObjType.FTS_PARSER;
        } else if (ctx.CONFIGURATION() != null) {
            type = DbObjType.FTS_CONFIGURATION;
        }

        for (Schema_qualified_nameContext objName :
            ctx.if_exist_names_restrict_cascade().names_references().name) {
            List<IdentifierContext> ids = objName.identifier();
            addFullObjReference(QNameParser.getSchemaName(ids, getDefSchemaName()),
                    QNameParser.getFirstName(ids), objName,
                    type, StatementActions.DROP, ctx.getParent());
        }
    }

    public void dropTrigger(Drop_trigger_statementContext ctx) {
        // FIXME table ref
        List<IdentifierContext> tableIds = ctx.table_name.identifier();
        addFullObjReference(QNameParser.getSchemaName(tableIds, getDefSchemaName()),
                QNameParser.getFirstName(tableIds), ctx.table_name, DbObjType.TABLE,
                StatementActions.NONE, ctx.getParent());

        addFullObjReference(null,
                ctx.name.getText(), ctx.name, DbObjType.TRIGGER,
                StatementActions.DROP, ctx.getParent());
    }

    public void dropRule(Drop_rule_statementContext ctx) {
        List<IdentifierContext> tableIds = ctx.schema_qualified_name().identifier();
        addFullObjReference(QNameParser.getSchemaName(tableIds, getDefSchemaName()),
                QNameParser.getFirstName(tableIds), ctx.schema_qualified_name(), DbObjType.TABLE,
                StatementActions.NONE, ctx.getParent());

        addFullObjReference(null, ctx.name.getText(), ctx.name, DbObjType.RULE,
                StatementActions.DROP, ctx.getParent());
    }

    public void dropFunction(Drop_function_statementContext ctx) {
        List<IdentifierContext> ids = ctx.function_parameters().name.identifier();
        addFullObjReference(QNameParser.getSchemaName(ids, getDefSchemaName()),
                QNameParser.getFirstName(ids), ctx.function_parameters().name,
                DbObjType.FUNCTION, StatementActions.DROP, ctx.getParent());
    }


    /**
     * Add object with start position to db object location List
     * @param schemaName - object schema name
     * @param ctx - object context
     * @param objType - object type
     */
    private void fillObjDefinition(String schemaName, IdentifierContext ctx, DbObjType objType) {
        int start = ctx.getStart().getStartIndex();
        String name = ctx.getText();
        if (ctx.QuotedIdentifier() != null) {
            start++;
        }

        PgObjLocation loc = new PgObjLocation(schemaName, name, null,
                start, filePath, ctx.getStart().getLine());
        loc.setAction(StatementActions.CREATE);
        loc.setObjType(objType);
        List<PgObjLocation> defs = definitions.get(filePath);
        if (defs == null) {
            defs = new ArrayList<>();
            definitions.put(filePath, defs);
        }
        defs.add(loc);
        List<PgObjLocation> refs = references.get(filePath);
        if (refs == null) {
            refs = new ArrayList<>();
            references.put(filePath, refs);
        }
        refs.add(loc);
    }

    private PgObjLocation addObjReference(String schemaName, String objName, DbObjType objType,
            StatementActions action, int startIndex, int lineNumber, String text) {
        PgObjLocation loc = new PgObjLocation(schemaName, objName, null,
                startIndex, filePath, lineNumber).setAction(action);
        loc.setText(text);
        loc.setObjType(objType);
        List<PgObjLocation> refs = references.get(filePath);
        if (refs == null) {
            refs = new ArrayList<>();
            references.put(filePath, refs);
        }
        refs.add(loc);
        return loc;
    }

    private void setCommentToDefinition(String objName, DbObjType objType, String comment) {
        for (List<PgObjLocation> locs: definitions.values()) {
            for (PgObjLocation loc : locs) {
                if (loc.getObjName().equals(objName) && loc.getObjType().equals(objType)) {
                    loc.setComment(comment);
                }
            }
        }
    }

    private void getTableConstraint(Constraint_commonContext ctx) {
        if (ctx.constr_body().FOREIGN() != null) {
            Table_referencesContext tblRef = ctx.constr_body().table_references();
            List<IdentifierContext> ids = tblRef.reftable.identifier();
            addFullObjReference(QNameParser.getSchemaName(ids, getDefSchemaName()),
                    QNameParser.getFirstName(ids), tblRef.reftable,
                    DbObjType.TABLE, StatementActions.NONE, ctx.getParent());
        }

        fillObjDefinition(null, ctx.constraint_name, DbObjType.CONSTRAINT);
    }

    public List<StatementBodyContainer> getStatementBodies() {
        return statementBodies;
    }
}
