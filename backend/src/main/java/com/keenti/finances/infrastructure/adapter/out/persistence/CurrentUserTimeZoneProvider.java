package com.keenti.finances.infrastructure.adapter.out.persistence;

import com.keenti.finances.domain.port.out.UserTimeZoneProvider;
import com.keenti.finances.infrastructure.adapter.in.rest.UserContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.ZoneId;

@ApplicationScoped
public class CurrentUserTimeZoneProvider implements UserTimeZoneProvider {

    @Inject
    UserContext userContext;

    @Override
    public ZoneId getTimeZone() {
        UserEntity user = UserEntity.findById(userContext.getUserId());
        String configured = user != null && user.timeZone != null
            ? user.timeZone
            : UserEntity.DEFAULT_TIME_ZONE;
        return ZoneId.of(configured);
    }
}
