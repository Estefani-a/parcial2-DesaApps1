package ar.jornadas.ui

import android.app.Application
import androidx.lifecycle.*
import ar.jornadas.Servicios
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import ar.jornadas.data.Charla
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart

/** Estado de la lista: sin edición válida todavía, o con edición (con o sin charlas). */
sealed interface Listado {
    object SinEdicion : Listado
    data class Con(val charlas: List<Charla>) : Listado
}

class ProgramaModel(app: Application, private val saved: SavedStateHandle) : AndroidViewModel(app) {
    private val servicios = Servicios.obtener(app)
    val mesa = servicios.mesa
    private val repo = servicios.agenda
    /** null = la base todavía no emitió. */
    val listado: StateFlow<Listado?> = combine(repo.programa, repo.hayEdicion) { charlas, hayEdicion ->
        if (hayEdicion) Listado.Con(charlas) else Listado.SinEdicion
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    // Filtros en SavedStateHandle: sobreviven a rotar y a la muerte del proceso, no a una tarea nueva.
    val dia: StateFlow<Int> = saved.getStateFlow("dia", 0)
    val tema: StateFlow<String> = saved.getStateFlow("tema", "Todos")
    fun elegirDia(valor: Int) { saved["dia"] = valor }
    fun elegirTema(valor: String) { saved["tema"] = valor }
    val aviso = MutableStateFlow("")
    val cargando: StateFlow<Boolean> = repo.cargando

    /** Carga automática: solo una por proceso, marcada al empezar el intento. */
    fun cargaInicial() {
        if (repo.reclamarCargaAutomatica()) recargar()
    }

    /** Actualización explícita o automática; se ignora si ya hay una petición en curso. */
    fun recargar() {
        if (!repo.tomarCarga()) return
        // ATOMIC: si el scope se cancela antes de arrancar, igual entra al try y libera la guarda.
        viewModelScope.launch(start = CoroutineStart.ATOMIC) {
            try {
                repo.sincronizar()
                aviso.value = ""
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                aviso.value = "No se pudo consultar el programa"
            } finally {
                repo.soltarCarga()
            }
        }
    }

    fun marcar(id: String) { viewModelScope.launch { repo.cambiarFavorita(id) } }
}
