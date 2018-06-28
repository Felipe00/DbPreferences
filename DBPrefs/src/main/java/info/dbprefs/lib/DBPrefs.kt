package info.dbprefs.lib

import android.arch.persistence.room.Room
import android.content.Context
import android.provider.Settings
import android.util.Log
import com.commonsware.cwac.saferoom.SafeHelperFactory
import com.google.gson.Gson
import info.dbprefs.lib.room.AppDatabase
import info.dbprefs.lib.room.entity.PreferenceRoom
import io.reactivex.Completable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import java.lang.reflect.Type

class DBPrefs {

    private var mParse: Parse

    init {
        mParse = GsonParser(Gson())
    }

    fun <T> put(key: ConfigKey, value: T): Boolean {
        return putSerialized<Any>(key, mParse.toJson(value))
    }

    fun <T> put(key: ConfigKey, value: List<T>): Boolean {
        return putSerialized<Any>(key, mParse.toJson(value))
    }

    fun <T> putSerialized(key: ConfigKey, value: String?): Boolean {
        if (value != null) {
            val pref = PreferenceRoom()
            pref.key = key.toString()
            pref.value = value
            appDatabase.preferenceDao().insert(pref)
        }
        return true
    }

    fun <T> get(key: ConfigKey, type: Type): T? {
        val returningClass: T?
        val decodedText = getSerialized(key) ?: return null
        try {
            returningClass = mParse.fromJson<T>(decodedText, type)
            return returningClass
        } catch (e: Exception) {
            Log.e(e.message, "Exception for class $type decoded Text: $decodedText")
        }

        return null
    }

    private fun getSerialized(key: ConfigKey?): String? {
        if (key == null) {
            throw IllegalArgumentException(ERROR_KEY_CANNOT_BE_NULL)
        }

        val start = System.currentTimeMillis()
        val value = appDatabase.preferenceDao().getValue(key.toString())
        return if (value == null)
            return null
        else
            return value.value
    }

    fun <T> get(key: ConfigKey, type: Type, defaultValue: T): T {
        val storage = get<T>(key, type)
        return if (storage == null || storage == "") {
            defaultValue
        } else storage
    }

    fun remove(key: ConfigKey) {
        appDatabase.preferenceDao().deleteByKey(key.toString())
    }

    fun contains(key: ConfigKey): Boolean {
        return appDatabase.preferenceDao().countKey(key.toString()) == 1
    }

    companion object {
        lateinit var appDatabase: AppDatabase

        fun init(context: Context) {
            init(context, Settings.Secure.getString(context.getApplicationContext().getContentResolver(), Settings.Secure.ANDROID_ID))
        }

        fun init(context: Context, password: String) {
            // Room
            val factory = SafeHelperFactory(password.toCharArray())
            appDatabase = Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.ROOM_DATABASE_NAME)
                    .openHelperFactory(factory).allowMainThreadQueries()
                    .build()

            val start = System.currentTimeMillis()

            // the first access need > 150 ms, to avoid the long time on main thread, it makes the first call in io-thread
            Completable.fromAction {
                appDatabase.preferenceDao().getValueFlowable("reduceFirstAccessTime")
            }.subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe { Log.d("time init", (System.currentTimeMillis() - start).toString() + " ms") }
        }

        fun destroy() {
            appDatabase.close()
        }

        private val ERROR_VALUE_CANNOT_BE_NULL = "Value cannot be null"
        private val ERROR_KEY_CANNOT_BE_NULL = "Key cannot be null"
        private val ERROR_COULD_NOT_PARSE_JSON_INTO = "could not parse json into "
    }

}
