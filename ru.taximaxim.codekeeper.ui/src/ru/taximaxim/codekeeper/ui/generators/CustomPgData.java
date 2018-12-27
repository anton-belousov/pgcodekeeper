package ru.taximaxim.codekeeper.ui.generators;

import java.util.Random;

/**
 * An implementation of a PostgreSql data generator for unimplemented types.
 *
 * @since 3.11.5
 * @author galiev_mr
 */
public class CustomPgData extends PgData<String> {

    public CustomPgData() {
        super(PgDataType.OTHER, "'data'", null, null); //$NON-NLS-1$
    }

    @Override
    public String generateValue() {
        return generator == PgDataGenerator.ANY ? start : null;
    }

    @Override
    protected String generateRandom(Random ran) {
        return null;
    }

    @Override
    public int getMaxValues() {
        return 1;
    }

    @Override
    public String valueFromString(String s) {
        return s;
    }
}
