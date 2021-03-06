package cz.startnet.utils.pgdiff.schema.system;

import java.io.IOException;
import java.io.Serializable;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Stream;

import cz.startnet.utils.pgdiff.loader.SupportedVersion;
import cz.startnet.utils.pgdiff.schema.IStatement;
import ru.taximaxim.codekeeper.apgdiff.ApgdiffConsts;
import ru.taximaxim.codekeeper.apgdiff.ApgdiffUtils;
import ru.taximaxim.codekeeper.apgdiff.log.Log;

public class PgSystemStorage implements Serializable {

    private static final long serialVersionUID = -5150584184929914163L;

    private static final ConcurrentMap<SupportedVersion, PgSystemStorage> STORAGE_CACHE = new ConcurrentHashMap<>();

    public static final String FILE_NAME = "SYSTEM_OBJECTS_";

    private final List<PgSystemCast> casts = new ArrayList<>();
    private final PgSystemSchema pgCatalogSchema = new PgSystemSchema(ApgdiffConsts.PG_CATALOG);
    private final PgSystemSchema informationSchema = new PgSystemSchema(ApgdiffConsts.INFORMATION_SCHEMA);
    private final List<PgSystemSchema> schemas = Collections.unmodifiableList(
            Arrays.asList(pgCatalogSchema, informationSchema));

    public static PgSystemStorage getObjectsFromResources(SupportedVersion ver) {
        SupportedVersion version;
        if (!SupportedVersion.VERSION_9_5.isLE(ver.getVersion())) {
            version = SupportedVersion.VERSION_9_5;
        } else {
            version = ver;
        }

        PgSystemStorage systemStorage = STORAGE_CACHE.get(version);
        if (systemStorage != null) {
            return systemStorage;
        }

        try {
            String path = ApgdiffUtils.getFileFromOsgiRes(PgSystemStorage.class.getResource(
                    FILE_NAME + version + ".ser")).toString();
            Object object = ApgdiffUtils.deserialize(path);

            if (object instanceof PgSystemStorage) {
                systemStorage = (PgSystemStorage) object;
                PgSystemStorage other = STORAGE_CACHE.putIfAbsent(version, systemStorage);
                return other == null ? systemStorage : other;
            }
        } catch (URISyntaxException | IOException e) {
            Log.log(Log.LOG_ERROR, "Error while reading systems objects from resources");
        }

        return null;
    }

    public void addCast(PgSystemCast cast) {
        casts.add(cast);
    }

    public PgSystemSchema getSchema(String schemaName) {
        if (ApgdiffConsts.PG_CATALOG.equals(schemaName)) {
            return pgCatalogSchema;
        } else if (ApgdiffConsts.INFORMATION_SCHEMA.equals(schemaName)) {
            return informationSchema;
        }
        return null;
    }

    public PgSystemSchema getPgCatalog() {
        return pgCatalogSchema;
    }

    public PgSystemSchema getInfoSchema() {
        return informationSchema;
    }

    public final Stream<IStatement> getDescendants() {
        List<IStatement> l = new ArrayList<>();
        casts.forEach(l::add);
        for (PgSystemSchema s : schemas) {
            l.add(s);
            s.getFunctions().forEach(l::add);
            s.getRelations().forEach(l::add);
        }

        return l.stream();
    }
}