package com.solvabit.phishingsmsdetector.screens.details

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.ml.naturallanguage.FirebaseNaturalLanguage
import com.google.firebase.ml.naturallanguage.translate.FirebaseTranslateLanguage
import com.google.firebase.ml.naturallanguage.translate.FirebaseTranslatorOptions
import com.solvabit.phishingsmsdetector.api.PhishingService
import com.solvabit.phishingsmsdetector.database.PhishedMessages
import com.solvabit.phishingsmsdetector.database.PhishingMessageDatabase
import com.solvabit.phishingsmsdetector.models.Message
import com.solvabit.phishingsmsdetector.models.Phishing
import com.solvabit.phishingsmsdetector.models.Phishing_Message
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Call
import retrofit2.Response

class MessageDetailsViewModel(val message: Message, val database: PhishingMessageDatabase) : ViewModel() {

    var msgExist: Boolean = false
    private val _hindiText = MutableLiveData<String>()
    val hindiText: LiveData<String>
        get() = _hindiText

    init {
        initializeTranslation(message.body)
        when(message._id) {
            -1 -> {
                checkPhishing(message)
                Log.i(TAG, "when : ${message._id} ")
            }
            else -> viewModelScope.launch {
                Log.i(TAG, "when : ${message._id} ")
                getPhishingData(message)
            }
        }
    }

    private suspend fun getPhishingData(message: Message) {

        val msgData = withContext(Dispatchers.IO) {
            Log.i(TAG, "withContext : ${message._id} ")
            database.phishingMessagesDao().getMessageFromId(message._id.toString())
        }

        Log.i(TAG, "outside withContext : ${msgData} ")
        if(msgData==null)
            checkPhishing(message)
    }

    private fun checkPhishing(message: Message) {

        Log.i(TAG, "inside checkPhishing : ${message._id} ")
        val text = message.body
        val phishingMessage = Phishing_Message(text)
        val phishingAPI = PhishingService.phishingAPInstance.checkPhishing(phishingMessage)
        phishingAPI.enqueue(object : retrofit2.Callback<Phishing> {
            override fun onResponse(call: Call<Phishing>, response: Response<Phishing>) {
                val reply = response.body()
                viewModelScope.launch {

                    val phishedMessage =
                        PhishedMessages(message._id.toString(), reply?.score ?: 0, reply?.result ?: false, message.address)
                    database.phishingMessagesDao().insertPhishedMessages(phishedMessage)
                }

            }

            override fun onFailure(call: Call<Phishing>, t: Throwable) {
                TODO("Not yet implemented")
            }

        })

    }

    private fun initializeTranslation(messageText: String) {

        val options = FirebaseTranslatorOptions.Builder()
            .setSourceLanguage(FirebaseTranslateLanguage.EN)
            .setTargetLanguage(FirebaseTranslateLanguage.HI)
            .build()
        val englishHindiTranslator = FirebaseNaturalLanguage.getInstance().getTranslator(options)

        englishHindiTranslator.downloadModelIfNeeded()
            .addOnSuccessListener {
                englishHindiTranslator.translate(messageText)
                    .addOnSuccessListener { translatedText ->
                        _hindiText.value = translatedText
                    }
                    .addOnFailureListener { exception ->
                        _hindiText.value = exception.message
                    }
            }
            .addOnFailureListener {
                Log.i(TAG, "initializeTranslation: Error tranlating - ${it.message}")
            }
    }

    companion object {
        private const val TAG = "MessageDetailsViewModel"
    }
}