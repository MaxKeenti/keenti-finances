package com.keenti.finances.domain.port.in;

import com.keenti.finances.domain.model.PublicSubscriptionView;
import java.util.Optional;

public interface PublicSubscriptionViewUseCase {
    Optional<PublicSubscriptionView> getByToken(String token);
}
