package org.flymine.dataloader;

import java.util.HashMap;
import java.util.Map;
import java.lang.reflect.Field;

/**
 * An object that stores field/value pairs from the database that should override the values in an
 * object retrieved from a data source.
 *
 * @author Matthew Wakeling
 */
public class IntegrationDescriptor
{
    private Map map;

    /**
     * Creates a new instance of an IntegrationDescriptor. The instance is empty - the put method
     * should be used to populate it.
     */
    public IntegrationDescriptor() {
        map = new HashMap();
    }

    /**
     * Puts a value into this object, referenced by the field. This indicates that the database
     * version of this particular attribute or object reference takes priority over the value in
     * the object retrieved from the data source.
     *
     * @param field the Field that the value should be associated with
     * @param value the value to store
     */
    public void put(Field field, Object value) {
        map.put(field, value);
    }

    /**
     * Retrieves a value from this object.
     *
     * @param field the Field that is to be updated in the object retrieved from the data source.
     * @return the value to replace the data source value
     */
    public Object get(Field field) {
        return map.get(field);
    }
}
