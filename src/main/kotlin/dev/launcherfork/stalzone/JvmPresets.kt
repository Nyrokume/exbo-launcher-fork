package dev.launcherfork.stalzone

/**
 * JVM presets from [EXBO-Community/stalcraft-jvm-optimization](https://github.com/EXBO-Community/stalcraft-jvm-optimization):
 * the released generators (`internal/profile` v1.0.4–v1.1.2) and `examples/aikar.json`.
 *
 * Heap rules follow those generators. Installed RAM is passed in as both total and free: the launcher does not
 * sample available memory. Large pages and the big-cache / slow-RAM branches stay off.
 * A preset whose generator would allocate no heap (too little RAM) is omitted (`null`).
 *
 * `heapGb` passed to [build] is the settings bar. When it is set, that heap replaces the generator's, and the
 * fields the generator derives from the heap are retuned (region size; on v1.0.x also metaspace, soft-ref and
 * code cache). GC and JIT flags stay, so the client's `-Xmx` and the wrapper's injected heap are the same number.
 */
object JvmPresets {
    data class Preset(val id: String, val version: String, val hintKey: String)

    data class Built(val heapGb: Int, val json: String)

    /** Newest release first, then the Aikar example. `v1.1.2/default` is the wrapper's current profile. */
    val all: List<Preset> = listOf(
        Preset("v1.1.2/default", "v1.1.2", "settings.preset.v112"),
        Preset("v1.1.1/default", "v1.1.1", "settings.preset.v111"),
        Preset("v1.1.0/default", "v1.1.0", "settings.preset.v110"),
        Preset("v1.0.8/default", "v1.0.8", "settings.preset.v108"),
        Preset("v1.0.7/default", "v1.0.7", "settings.preset.v107"),
        Preset("v1.0.6/default", "v1.0.6", "settings.preset.v106"),
        Preset("v1.0.5/default", "v1.0.5", "settings.preset.v105"),
        Preset("v1.0.4/default", "v1.0.4", "settings.preset.v104"),
        Preset("aikar", "aikar", "settings.preset.aikar"),
    )

    const val CURRENT = "v1.1.2/default"

    fun build(id: String, totalGb: Int, freeGb: Int, threads: Int, cores: Int, heapGb: Int? = null): Built? {
        if (id == "aikar") {
            val heap = heapGb?.takeIf { it > 0 } ?: 6
            return Built(heap, applyHeapToJson(id, AIKAR, heap))
        }
        val cfg = when (id) {
            "v1.1.2/default", "v1.1.1/default" -> v111(totalGb, threads)
            "v1.1.0/default" -> v110(totalGb, threads)
            "v1.0.8/default" -> v107(totalGb, freeGb, cores, minHeap = 4, dontCompileHugeOnWeak = true)
            "v1.0.7/default" -> v107(totalGb, freeGb, cores, minHeap = 6, dontCompileHugeOnWeak = false)
            "v1.0.6/default" -> v106(totalGb, freeGb, threads)
            "v1.0.5/default", "v1.0.4/default" -> v104(totalGb, freeGb, threads)
            else -> null
        } ?: return null
        if (cfg.heap <= 0) return null
        val chosen = heapGb?.takeIf { it > 0 }
        if (chosen != null && chosen != cfg.heap) applyHeap(id, cfg, chosen)
        return Built(cfg.heap, cfg.json())
    }

    /**
     * Writes [heapGb] into a profile `service.exe` already has on disk.
     * Region size follows the preset; v1.0.x also retunes metaspace, soft-ref and (v1.0.4–v1.0.6) the code cache.
     * Every other flag stays, including the hand-tuned 8 kHz region. A file without `heap_size_gb` is unchanged.
     */
    fun applyHeapToJson(id: String, json: String, heapGb: Int): String {
        if (heapGb <= 0 || !Regex(""""heap_size_gb"\s*:""").containsMatchIn(json)) return json
        var text = replaceNum(json, "heap_size_gb", heapGb)
        regionMb(id, heapGb)?.let { text = replaceNum(text, "g1_heap_region_size_mb", it) }
        if (id.startsWith("v1.0.")) {
            text = replaceNum(text, "metaspace_mb", legacyMetaspace(heapGb))
            text = replaceNum(text, "soft_ref_lru_policy_ms_per_mb", legacySoftRef(heapGb))
            if (id == "v1.0.6/default" || id == "v1.0.5/default" || id == "v1.0.4/default") {
                text = replaceNum(text, "reserved_code_cache_size_mb", legacyCodeCache(heapGb))
            }
        }
        return text
    }

    private fun applyHeap(id: String, cfg: Cfg, heap: Int) {
        cfg.heap = heap
        regionMb(id, heap)?.let { cfg.regionMb = it }
        if (id.startsWith("v1.0.")) {
            cfg.metaspace = legacyMetaspace(heap)
            cfg.softRef = legacySoftRef(heap)
            if (id == "v1.0.6/default" || id == "v1.0.5/default" || id == "v1.0.4/default") {
                cfg.codeCache = legacyCodeCache(heap)
            }
        }
    }

    /** Region the generator would pick for [heap]. Null for presets whose region is not a function of the heap. */
    private fun regionMb(id: String, heap: Int): Int? = when (id) {
        "v1.1.2/default", "v1.1.1/default", "default" -> if (heap <= 3) 4 else 8
        "v1.1.0/default" -> when {
            heap <= 3 -> 4
            heap <= 5 -> 8
            else -> 16
        }
        "v1.0.8/default", "v1.0.7/default" -> legacyRegionV107(heap)
        "v1.0.6/default", "v1.0.5/default", "v1.0.4/default" -> legacyRegionV104(heap)
        else -> null
    }

    private fun replaceNum(json: String, key: String, value: Int): String =
        json.replace(Regex(""""$key"\s*:\s*\d+"""), """"$key": $value""")

    private fun v111(totalGb: Int, threads: Int): Cfg {
        val heap = when {
            totalGb >= 16 -> 6
            totalGb >= 12 -> 5
            totalGb >= 8 -> 4
            totalGb >= 6 -> 3
            else -> 2
        }
        val (parallel, concurrent) = modernThreads(threads)
        val slow = false
        return modernBase(heap, totalGb, parallel, concurrent).apply {
            pauseMs = if (slow) 150 else 100
            regionMb = if (heap <= 3) 4 else 8
            newSize = if (slow) 30 else 33
            maxNew = 50
            reserve = 15
            waste = 10
            mixed = if (slow) 4 else 6
            ihop = 25
            live = 85
            rset = if (slow) 5 else 8
            survivor = 12
            tenuring = 3
            dynamicThreads = true
            dedup = false
            softRef = 50
        }
    }

    private fun v110(totalGb: Int, threads: Int): Cfg {
        val heap = when {
            totalGb >= 24 -> 8
            totalGb >= 16 -> 6
            totalGb >= 12 -> 5
            totalGb >= 8 -> 4
            totalGb >= 6 -> 3
            else -> 2
        }
        val (parallel, concurrent) = modernThreads(threads)
        val bigCache = false
        return modernBase(heap, totalGb, parallel, concurrent).apply {
            pauseMs = if (bigCache) 25 else 50
            regionMb = when {
                heap <= 3 -> 4
                heap <= 5 -> 8
                else -> 16
            }
            newSize = if (bigCache) 30 else 23
            maxNew = 50
            reserve = 20
            waste = 5
            mixed = if (bigCache) 4 else 3
            ihop = if (bigCache) 15 else 20
            live = 90
            rset = 0
            survivor = 32
            tenuring = 1
            dynamicThreads = true
            dedup = true
            softRef = if (bigCache) 50 else 25
            if (bigCache) {
                inlineLevel = 20
                freqInline = 750
                inlineSmall = 6000
                nodeLimit = 320000
            }
        }
    }

    private fun modernBase(heap: Int, totalGb: Int, parallel: Int, concurrent: Int) = Cfg().apply {
        this.heap = heap
        preTouch = totalGb >= 12
        metaspace = 512
        satb = 30
        hotCard = 16
        refineMs = 150
        gcRatio = 99
        this.parallel = parallel
        this.concurrent = concurrent
        codeCache = 400
        inlineLevel = 15
        freqInline = 500
        inlineSmall = 4000
        nodeLimit = 240000
        nodeFudge = 8000
        sweep = 1
        dontCompileHuge = false
        prefetch = 3
        serverClass = true
        xmm = true
        fpu = true
        reflect = 0
        autoBox = 4096
        priorities = true
        priorityPolicy = 1
        counterDecay = false
        compileScale = 0.5
    }

    private fun v104(totalGb: Int, freeGb: Int, threads: Int): Cfg? {
        val heap = legacyHeapV104(totalGb, freeGb)
        if (heap <= 0) return null
        val (parallel, concurrent) = legacyThreadsV104(threads)
        val (survivorRatio, tenuringThreshold) = legacySurvivor(threads)
        return legacyBase(heap, parallel, concurrent).apply {
            preTouch = true
            pauseMs = 50
            regionMb = legacyRegionV104(heap)
            newSize = 30
            maxNew = 40
            reserve = 15
            waste = 5
            mixed = 4
            ihop = 35
            live = 90
            rset = 5
            survivor = survivorRatio
            tenuring = tenuringThreshold
            softRef = legacySoftRef(heap)
            codeCache = legacyCodeCache(heap)
            inlineLevel = 15
            freqInline = 500
            dontCompileHuge = true
            reflect = 15
            counterDecay = true
        }
    }

    private fun v106(totalGb: Int, freeGb: Int, threads: Int): Cfg? {
        val heap = legacyHeapV106(totalGb, freeGb)
        if (heap <= 0) return null
        val built = v104(totalGb, freeGb, threads) ?: return null
        built.heap = heap
        built.regionMb = legacyRegionV104(heap)
        built.metaspace = legacyMetaspace(heap)
        built.softRef = legacySoftRef(heap)
        built.codeCache = legacyCodeCache(heap)
        return built
    }

    private fun v107(totalGb: Int, freeGb: Int, cores: Int, minHeap: Int, dontCompileHugeOnWeak: Boolean): Cfg? {
        val heap = legacyHeapV107(totalGb, freeGb, minHeap)
        if (heap <= 0) return null
        val (parallel, concurrent) = legacyThreadsV107(cores)
        val strong = cores >= 8
        return legacyBase(heap, parallel, concurrent).apply {
            preTouch = strong
            pauseMs = 50
            regionMb = legacyRegionV107(heap)
            newSize = 23
            maxNew = 40
            reserve = 20
            waste = 5
            mixed = 4
            live = 90
            survivor = 32
            tenuring = 1
            softRef = legacySoftRef(heap)
            inlineLevel = 15
            freqInline = 500
            reflect = 15
            counterDecay = true
            if (strong) {
                ihop = 15
                rset = 0
                satb = 30
                hotCard = 16
                refineMs = 150
                gcRatio = 99
                dynamicThreads = true
                dedup = true
                codeCache = 400
                inlineSmall = 4000
                nodeLimit = 240000
                nodeFudge = 8000
                sweep = 1
                dontCompileHuge = false
                prefetch = 3
                serverClass = true
                xmm = true
                fpu = true
            } else {
                ihop = 30
                rset = 5
                gcRatio = 19
                codeCache = 256
                dontCompileHuge = dontCompileHugeOnWeak
            }
        }
    }

    private fun legacyBase(heap: Int, parallel: Int, concurrent: Int) = Cfg().apply {
        this.heap = heap
        metaspace = legacyMetaspace(heap)
        this.parallel = parallel
        this.concurrent = concurrent
    }

    private fun modernThreads(threads: Int): Pair<Int, Int> {
        val parallel = (threads - 2).coerceIn(2, 10)
        val concurrent = (parallel / 2).coerceIn(1, 5)
        return parallel to concurrent
    }

    private fun legacyThreadsV104(threads: Int): Pair<Int, Int> {
        val parallel = (threads - 2).coerceAtLeast(2)
        val concurrent = (parallel / 4).coerceAtLeast(1)
        return parallel to concurrent
    }

    private fun legacyThreadsV107(cores: Int): Pair<Int, Int> {
        val parallel = (cores / 2).coerceAtLeast(2)
        val concurrent = (cores / 4).coerceAtLeast(1)
        return parallel to concurrent
    }

    private fun legacyHeapV104(totalGb: Int, freeGb: Int): Int {
        if (totalGb <= 8) return 0
        var heap = freeGb / 2
        var floor = totalGb / 4
        if (floor < 6) floor = 6
        var cap = totalGb * 3 / 4
        if (cap > 16) cap = 16
        if (heap < floor) heap = floor
        if (heap > cap) heap = cap
        if (heap < 6) heap = 6
        return heap
    }

    private fun legacyHeapV106(totalGb: Int, freeGb: Int): Int {
        if (totalGb <= 8 || freeGb < 6) return 0
        return (freeGb / 2).coerceIn(6, 8)
    }

    private fun legacyHeapV107(totalGb: Int, freeGb: Int, minHeap: Int): Int {
        if (totalGb <= 8 || freeGb < 6) return 0
        return (freeGb / 2).coerceAtLeast(minHeap).coerceAtMost(8)
    }

    private fun legacyRegionV104(heap: Int) = when {
        heap <= 4 -> 4
        heap <= 8 -> 8
        heap <= 16 -> 16
        else -> 32
    }

    private fun legacyRegionV107(heap: Int) = when {
        heap <= 4 -> 8
        heap <= 8 -> 16
        else -> 32
    }

    private fun legacyMetaspace(heap: Int) = when {
        heap <= 4 -> 128
        heap <= 8 -> 256
        else -> 512
    }

    private fun legacyCodeCache(heap: Int) = (heap * 1024 / 16).coerceIn(128, 512)

    private fun legacySurvivor(threads: Int) = if (threads <= 4) 32 to 1 else 8 to 4

    private fun legacySoftRef(heap: Int) = when {
        heap <= 4 -> 10
        heap <= 8 -> 25
        else -> 50
    }

    private class Cfg {
        var heap = 0
        var preTouch = false
        var metaspace = 0
        var pauseMs = 0
        var regionMb = 0
        var newSize = 0
        var maxNew = 0
        var reserve = 0
        var waste = 0
        var mixed = 0
        var ihop = 0
        var live = 0
        var rset = 0
        var survivor = 0
        var tenuring = 0
        var satb = 0
        var hotCard = 0
        var refineMs = 0
        var gcRatio = 0
        var dynamicThreads = false
        var dedup = false
        var parallel = 0
        var concurrent = 0
        var softRef = 0
        var codeCache = 0
        var inlineLevel = 0
        var freqInline = 0
        var inlineSmall = 0
        var nodeLimit = 0
        var nodeFudge = 0
        var sweep = 0
        var dontCompileHuge = false
        var prefetch = 0
        var serverClass = false
        var xmm = false
        var fpu = false
        var reflect = 0
        var autoBox = 0
        var priorities = false
        var priorityPolicy = 0
        var counterDecay = false
        var compileScale = 0.0

        fun json(): String {
            val fields = listOf(
                "heap_size_gb" to heap,
                "pre_touch" to preTouch,
                "metaspace_mb" to metaspace,
                "max_gc_pause_millis" to pauseMs,
                "g1_heap_region_size_mb" to regionMb,
                "g1_new_size_percent" to newSize,
                "g1_max_new_size_percent" to maxNew,
                "g1_reserve_percent" to reserve,
                "g1_heap_waste_percent" to waste,
                "g1_mixed_gc_count_target" to mixed,
                "initiating_heap_occupancy_percent" to ihop,
                "g1_mixed_gc_live_threshold_percent" to live,
                "g1_rset_updating_pause_time_percent" to rset,
                "survivor_ratio" to survivor,
                "max_tenuring_threshold" to tenuring,
                "g1_satb_buffer_enqueuing_threshold_percent" to satb,
                "g1_conc_rs_hot_card_limit" to hotCard,
                "g1_conc_refinement_service_interval_millis" to refineMs,
                "gc_time_ratio" to gcRatio,
                "use_dynamic_number_of_gc_threads" to dynamicThreads,
                "use_string_deduplication" to dedup,
                "parallel_gc_threads" to parallel,
                "conc_gc_threads" to concurrent,
                "soft_ref_lru_policy_ms_per_mb" to softRef,
                "reserved_code_cache_size_mb" to codeCache,
                "max_inline_level" to inlineLevel,
                "freq_inline_size" to freqInline,
                "inline_small_code" to inlineSmall,
                "max_node_limit" to nodeLimit,
                "node_limit_fudge_factor" to nodeFudge,
                "nmethod_sweep_activity" to sweep,
                "dont_compile_huge_methods" to dontCompileHuge,
                "allocate_prefetch_style" to prefetch,
                "always_act_as_server_class" to serverClass,
                "use_xmm_for_array_copy" to xmm,
                "use_fpu_for_spilling" to fpu,
                "use_large_pages" to false,
                "reflection_inflation_threshold" to reflect,
                "auto_box_cache_max" to autoBox,
                "use_thread_priorities" to priorities,
                "thread_priority_policy" to priorityPolicy,
                "use_counter_decay" to counterDecay,
                "compile_threshold_scaling" to compileScale,
            )
            return fields.joinToString(",\n", "{\n", "\n}\n") { (name, value) ->
                "  \"$name\": ${jsonValue(value)}"
            }
        }
    }

    private fun jsonValue(value: Any): String = when (value) {
        is Boolean -> value.toString()
        is Double -> if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()
        else -> value.toString()
    }

    /** `examples/aikar.json` from the same repository. Hardware-independent. */
    private val AIKAR: String = """
{
  "heap_size_gb": 6,
  "pre_touch": true,
  "metaspace_mb": 512,
  "max_gc_pause_millis": 150,
  "g1_heap_region_size_mb": 8,
  "g1_new_size_percent": 30,
  "g1_max_new_size_percent": 40,
  "g1_reserve_percent": 20,
  "g1_heap_waste_percent": 5,
  "g1_mixed_gc_count_target": 4,
  "initiating_heap_occupancy_percent": 15,
  "g1_mixed_gc_live_threshold_percent": 90,
  "g1_rset_updating_pause_time_percent": 5,
  "survivor_ratio": 32,
  "max_tenuring_threshold": 1,
  "g1_satb_buffer_enqueuing_threshold_percent": 30,
  "g1_conc_rs_hot_card_limit": 16,
  "g1_conc_refinement_service_interval_millis": 150,
  "gc_time_ratio": 99,
  "use_dynamic_number_of_gc_threads": true,
  "use_string_deduplication": false,
  "parallel_gc_threads": 8,
  "conc_gc_threads": 3,
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
  "thread_priority_policy": 1,
  "use_counter_decay": false,
  "compile_threshold_scaling": 0.5
}
""".trimIndent().trim() + "\n"
}
