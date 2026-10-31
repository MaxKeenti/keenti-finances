package com.keenti.finances.infrastructure.adapter.out.persistence;

import com.keenti.finances.domain.model.Currencies;
import com.keenti.finances.domain.port.out.PrimaryCurrencyProvider;
import com.keenti.finances.infrastructure.adapter.in.rest.UserContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class CurrentUserPrimaryCurrencyProvider implements PrimaryCurrencyProvider {

    @Inject
    UserContext userContext;

    @Override
    public String primaryCurrency() {
        UserEntity user = UserEntity.findById(userContext.getUserId());
        return user != null && Currencies.isSupported(user.primaryCurrency)
            ? user.primaryCurrency
            : Currencies.DEFAULT;
    }
}
