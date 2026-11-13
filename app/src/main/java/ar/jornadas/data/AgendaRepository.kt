package ar.jornadas.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.atomic.AtomicBoolean

class AgendaRepository(private val api: ProgramaApi, private val dao: CarteleraDao) {
    val programa = dao.observar()
    val hayEdicion = dao.hayEdicion()

    // Estado de proceso: el repositorio es único (ver Servicios.obtener).
    private val enCurso = AtomicBoolean(false)
    private val automaticaHecha = AtomicBoolean(false)
    private val _cargando = MutableStateFlow(false)
    val cargando: StateFlow<Boolean> = _cargando

    /** Reserva atómicamente la única petición en curso. Si devuelve true, hay que llamar a [soltarCarga]. */
    fun tomarCarga(): Boolean = enCurso.compareAndSet(false, true).also { if (it) _cargando.value = true }

    fun soltarCarga() {
        enCurso.set(false)
        _cargando.value = false
    }

    /** true solo la primera vez en el proceso: marca la carga automática como hecha. */
    fun reclamarCargaAutomatica(): Boolean = automaticaHecha.compareAndSet(false, true)

    suspend fun sincronizar() {
        val fichas = api.consultar().map {
            Charla(it.id, it.title, it.track, it.day, it.time, it.room, it.speaker, it.summary)
        }
        dao.aplicarInstantanea(fichas, System.currentTimeMillis())
    }

    suspend fun cambiarFavorita(id: String) = dao.alternar(id)
}
