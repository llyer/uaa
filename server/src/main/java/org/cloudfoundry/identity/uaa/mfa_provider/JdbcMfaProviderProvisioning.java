package org.cloudfoundry.identity.uaa.mfa_provider;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.cloudfoundry.identity.uaa.audit.event.SystemDeletable;
import org.cloudfoundry.identity.uaa.provider.IdpAlreadyExistsException;
import org.cloudfoundry.identity.uaa.util.JsonUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementSetter;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.util.StringUtils;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.UUID;

public class JdbcMfaProviderProvisioning implements MfaProviderProvisioning, SystemDeletable {

    private static Log logger = LogFactory.getLog(JdbcMfaProviderProvisioning.class);
    public static final String MFA_PROVIDER_FIELDS = "id,name,type,config,active,identity_zone_id,created,lastmodified";
    public static final String CREATE_PROVIDER_SQL = "insert into mfa_providers(" + MFA_PROVIDER_FIELDS + ") values (?,?,?,?,?,?,?,?)";
    public static final String MFA_PROVIDER_BY_ID_QUERY = "select " + MFA_PROVIDER_FIELDS + " from mfa_providers " + "where id=? and identity_zone_id=?";
    protected final JdbcTemplate jdbcTemplate;
    private MfaProviderValidator mfaProviderValidator;
    private MfaProviderMapper mapper = new MfaProviderMapper();

    public JdbcMfaProviderProvisioning(JdbcTemplate jdbcTemplate, MfaProviderValidator mfaProviderValidator) {
        this.jdbcTemplate = jdbcTemplate;
        this.mfaProviderValidator = mfaProviderValidator;
    }

    @Override
    public MfaProvider create(MfaProvider provider, String zoneId) {
        mfaProviderValidator.validate(provider);
        final String id = UUID.randomUUID().toString();
        try {
            jdbcTemplate.update(CREATE_PROVIDER_SQL, new PreparedStatementSetter() {
                @Override
                public void setValues(PreparedStatement ps) throws SQLException {
                    int pos = 1;
                    ps.setString(pos++, id);
                    ps.setString(pos++, provider.getName());
                    ps.setString(pos++, provider.getType().toValue());
                    ps.setString(pos++, JsonUtils.writeValueAsString(provider.getConfig()));
                    ps.setBoolean(pos++, provider.isActive());
                    ps.setString(pos++, zoneId);
                    ps.setTimestamp(pos++, new Timestamp(System.currentTimeMillis()));
                    ps.setTimestamp(pos++, new Timestamp(System.currentTimeMillis()));
                }
            });
        } catch (DuplicateKeyException e) {
            throw new IdpAlreadyExistsException(e.getMostSpecificCause().getMessage());
        }
        return retrieve(id, zoneId);
    }

    @Override
    public MfaProvider retrieve(String id, String zoneId) {
        MfaProvider provider = jdbcTemplate.queryForObject(MFA_PROVIDER_BY_ID_QUERY, mapper, id, zoneId);
        return provider;
    }



    @Override
    public int deleteByIdentityZone(String zoneId) {
        return 0;
    }

    @Override
    public int deleteByOrigin(String origin, String zoneId) {
        return 0;
    }

    @Override
    public int deleteByClient(String clientId, String zoneId) {
        return 0;
    }

    @Override
    public int deleteByUser(String userId, String zoneId) {
        return 0;
    }

    @Override
    public Log getLogger() {
        return logger;
    }

    private static final class MfaProviderMapper implements RowMapper<MfaProvider> {
        @Override
        public MfaProvider mapRow(ResultSet rs, int rowNum) throws SQLException {
            MfaProvider result =  new MfaProvider();
            int pos = 1;

            result.setId(rs.getString(pos++).trim());
            result.setName(rs.getString(pos++));
            result.setType(MfaProvider.MfaProviderType.forValue(rs.getString(pos++)));
            //deserialize based on type
            String config = rs.getString(pos++);
            AbstractMfaProviderConfig definition = null;
            switch(result.getType()) {
                case GOOGLE_AUTHENTICATOR:
                    definition = StringUtils.hasText(config) ? JsonUtils.readValue(config, GoogleMfaProviderConfig.class) : new GoogleMfaProviderConfig();
                    break;
                default:
                    break;
            }
            result.setConfig(definition);
            result.setActive(rs.getBoolean(pos++));
            result.setIdentityZoneId(rs.getString(pos++));
            result.setCreated(rs.getTimestamp(pos++));
            result.setLastModified(rs.getTimestamp(pos++));

            return result;
        }
    }
}
