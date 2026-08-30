package com.sbi.surakshasathi.feature.ragwarning.domain.notifier

import com.sbi.surakshasathi.feature.messagescan.domain.model.Message
import com.sbi.surakshasathi.feature.ragwarning.domain.model.RagWarning

/**
 * Domain-layer contract for surfacing a [RagWarning] to the user as a system
 * notification (§4b). Kept as an interface so the domain layer stays free of
 * Android imports (NotificationCompat, Context, etc. live in the
 * implementation: [com.sbi.surakshasathi.feature.ragwarning.data.notifier.RagWarningNotifier]).
 */
interface RagWarningDispatcher {
    fun notify(
        message: Message,
        warning: RagWarning,
    )
}
