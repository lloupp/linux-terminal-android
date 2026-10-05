package com.linuxterminal.android.agent.permissions

enum class Access { READ, WRITE, EXECUTE, DESTRUCTIVE }
data class PermissionRequest(val sessionId: String, val tool: String, val access: Access, val resource: String,
    val requiresApproval: Boolean = false, val parameters: Map<String, String> = emptyMap())
fun interface PermissionGate { fun allows(request: PermissionRequest): Boolean }
/** Secure default. No implicit approval inferred from a model's arguments. */
class ReadOnlyGate : PermissionGate {
    override fun allows(request: PermissionRequest) = request.access == Access.READ && !request.requiresApproval
}
