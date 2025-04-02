package com.example.mapsproject.utils

import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import com.example.mapsproject.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText

object Dialogs {
    fun setUpNameForDialogs(context : Context) : String{
        val customView = LayoutInflater.from(context).inflate(R.layout.text_field_for_name,null)
        val inputText = customView.findViewById<TextInputEditText>(R.id.inputText)
        var userInput = ""

        val builder = MaterialAlertDialogBuilder(context)
        builder.setTitle("Enter Text")
        builder.setView(customView)

        builder.setPositiveButton("OK") { dialog, _ ->
             userInput = inputText.text.toString()
            Log.d("UserInput", userInput)

            dialog.dismiss()
        }

        builder.setNegativeButton("Cancel") { dialog, _ ->
            userInput = "cancel"
            dialog.dismiss()

        }

        builder.show()
        return userInput
    }
}