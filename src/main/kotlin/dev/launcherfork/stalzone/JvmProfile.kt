package dev.launcherfork.stalzone

/**
 * The STALZONE JVM wrapper profile (`Stalcraft-JVM-Flag-fix`).
 * Activating it writes `configs/<name>.json` and `HKCU\Software\StalcraftWrapper\ActiveConfig`,
 * which `service.exe` already reads on the next game start. The launcher does not install IFEO.
 */
object JvmProfile {
    const val ACTIVE_KEY = "Software\\StalcraftWrapper"
    const val ACTIVE_VALUE = "ActiveConfig"
    const val RECOMMENDED = "8khz"

    /** Heap 6 GB, pause goal 20 ms, 16 MB regions: the preset checked against OpenJDK 9.0.6.4. */
    const val TUNED_8KHZ: String = """
{
  "heap_size_gb": 6,
  "pre_touch": true,
  "metaspace_mb": 512,
  "max_gc_pause_millis": 20,
  "g1_heap_region_size_mb": 16,
  "g1_new_size_percent": 20,
  "g1_max_new_size_percent": 35,
  "g1_reserve_percent": 20,
  "g1_heap_waste_percent": 5,
  "g1_mixed_gc_count_target": 8,
  "initiating_heap_occupancy_percent": 35,
  "g1_mixed_gc_live_threshold_percent": 90,
  "g1_rset_updating_pause_time_percent": 0,
  "survivor_ratio": 16,
  "max_tenuring_threshold": 4,
  "g1_satb_buffer_enqueuing_threshold_percent": 30,
  "g1_conc_rs_hot_card_limit": 16,
  "g1_conc_refinement_service_interval_millis": 150,
  "gc_time_ratio": 99,
  "use_dynamic_number_of_gc_threads": false,
  "use_string_deduplication": false,
  "parallel_gc_threads": 4,
  "conc_gc_threads": 2,
  "soft_ref_lru_policy_ms_per_mb": 50,
  "reserved_code_cache_size_mb": 400,
  "max_inline_level": 15,
  "freq_inline_size": 500,
  "inline_small_code": 4000,
  "max_node_limit": 240000,
  "node_limit_fudge_factor": 8000,
  "nmethod_sweep_activity": 1,
  "dont_compile_huge_methods": false,
  "allocate_prefetch_style": 3,
  "always_act_as_server_class": true,
  "use_xmm_for_array_copy": true,
  "use_fpu_for_spilling": true,
  "use_large_pages": false,
  "reflection_inflation_threshold": 0,
  "auto_box_cache_max": 4096,
  "use_thread_priorities": true,
  "thread_priority_policy": 0,
  "use_counter_decay": false,
  "compile_threshold_scaling": 0.5
}
"""

    fun tunedJson(): String = TUNED_8KHZ.trimIndent().trim() + "\n"
}
