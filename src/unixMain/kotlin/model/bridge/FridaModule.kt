package model.bridge

interface FridaModule {
    fun device_attach_sync(): Unit
    fun device_get_process_by_name_sync(): Unit
    fun device_get_process_by_pid_sync(): Unit
    fun device_manager_add_remote_device_sync(): Unit
    fun device_manager_enumerate_devices_sync(): Unit
    fun device_manager_get_device_by_type_sync(): Unit
    fun device_manager_new(): Unit
    fun init(): Unit
    fun process_get_pid(): Unit
    fun remote_device_options_new(): Unit
    fun script_load_sync(): Unit
    fun script_options_new(): Unit
    fun script_options_set_name(): Unit
    fun script_post(): Unit
    fun session_create_script_sync(): Unit
    fun session_is_detached(): Unit
}