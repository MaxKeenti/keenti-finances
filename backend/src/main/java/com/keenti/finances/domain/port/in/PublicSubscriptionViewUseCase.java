package com.keenti.finances.domain.port.in;

import com.keenti.finances.domain.model.PublicSubscriptionView;
import java.util.Optional;

/**
 * Capability-token boundary for the unauthenticated Public Subscription View.
 *
 * <p>The raw token is lookup input only. It must never be logged, fingerprinted,
 * or included in an exception, request path, or capability URL passed to
 * application logging. Observability may record only a constant event name,
 * lookup result, HTTP status, and the resolved Subscription ID after success.
 */
public interface PublicSubscriptionViewUseCase {
    Optional<PublicSubscriptionView> getByToken(String token);
}
