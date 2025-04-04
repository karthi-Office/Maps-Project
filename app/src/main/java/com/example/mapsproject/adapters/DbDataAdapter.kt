package com.example.mapsproject.adapters

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.mapsproject.databinding.RecyclerViewCardBinding
import com.example.mapsproject.model.LatLangEntity

class DbDataAdapter (private val onItemClick : (LatLangEntity) -> Unit): RecyclerView.Adapter<DbDataAdapter.DbDataViewHolder>() {
    private var dBList = emptyList<LatLangEntity>()
    inner class DbDataViewHolder(private val binding : RecyclerViewCardBinding) : RecyclerView.ViewHolder(binding.root){
        @SuppressLint("SetTextI18n")
        fun bind(item : LatLangEntity){
            binding.apply {
//                itemId.text = item.id.toString()
                itemName.text = item.locationName
                root.setOnClickListener {
                    onItemClick(item)
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DbDataViewHolder {
            val binding = RecyclerViewCardBinding.inflate(LayoutInflater.from(parent.context),parent,false)
        return DbDataViewHolder(binding)
    }

    override fun getItemCount(): Int = dBList.size


    override fun onBindViewHolder(holder: DbDataViewHolder, position: Int) {
                 holder.bind(dBList[position])
         }

    fun setData(newList : List<LatLangEntity>){
        dBList = newList
        notifyDataSetChanged()
    }
}