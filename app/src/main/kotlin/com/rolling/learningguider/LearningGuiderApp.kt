package com.rolling.learningguider

import android.app.Application
import android.content.Context
import androidx.room.Room
import com.rolling.learningguider.data.db.AppDatabase
import com.rolling.learningguider.data.repo.KbRepository

/** 手动依赖容器（项目规模小，不引入 Hilt） */
class AppContainer(context: Context) {
    val database: AppDatabase = Room.databaseBuilder(
        context.applicationContext,
        AppDatabase::class.java,
        AppDatabase.NAME,
    ).build()

    val repository: KbRepository by lazy {
        KbRepository(context.applicationContext.assets, database)
    }
}

class LearningGuiderApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
