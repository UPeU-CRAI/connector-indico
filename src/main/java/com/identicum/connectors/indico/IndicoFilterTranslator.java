package com.identicum.connectors.indico;

import org.identityconnectors.common.logging.Log;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.AttributeUtil;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.Uid;
import org.identityconnectors.framework.common.objects.filter.AbstractFilterTranslator;
import org.identityconnectors.framework.common.objects.filter.EqualsFilter;

/**
 * Translates ConnId filters into Indico-specific search filters.
 */
public class IndicoFilterTranslator extends AbstractFilterTranslator<IndicoFilter> {

    private static final Log LOG = Log.getLog(IndicoFilterTranslator.class);

    @Override
    protected IndicoFilter createEqualsExpression(EqualsFilter filter, boolean not) {
        if (not) {
            LOG.warn("NOT filters are not supported by Indico connector");
            return null;
        }
        Attribute attribute = filter.getAttribute();
        String attrName = attribute.getName();
        String value = AttributeUtil.getAsStringValue(attribute);
        if (value == null) {
            LOG.ok("Ignoring filter for attribute {0} without value", attrName);
            return null;
        }
        IndicoFilter result = new IndicoFilter();
        if (Uid.NAME.equals(attrName) || Name.NAME.equals(attrName)) {
            result.setRegistrationId(value);
            return result;
        }
        if ("email".equalsIgnoreCase(attrName)) {
            result.setEmail(value);
            return result;
        }
        if ("eventId".equals(attrName)) {
            try {
                result.setEventId(Long.valueOf(value));
                return result;
            } catch (NumberFormatException e) {
                LOG.warn(e, "Cannot parse eventId value {0}", value);
                return null;
            }
        }
        LOG.ok("Attribute {0} is not supported for filtering", attrName);
        return null;
    }
}
