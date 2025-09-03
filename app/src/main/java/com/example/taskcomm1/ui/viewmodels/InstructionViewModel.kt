package com.example.taskcomm1.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.taskcomm1.data.models.Instruction
import com.example.taskcomm1.data.repository.TaskCommRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class InstructionViewModel(
    private val repository: TaskCommRepository
) : ViewModel() {
    
    private val _instructions = MutableStateFlow<List<Instruction>>(emptyList())
    val instructions: StateFlow<List<Instruction>> = _instructions.asStateFlow()
    
    private val _instructionState = MutableStateFlow<InstructionState>(InstructionState.Idle)
    val instructionState: StateFlow<InstructionState> = _instructionState.asStateFlow()
    
    fun loadInstructions(userId: String) {
        viewModelScope.launch {
            _instructionState.value = InstructionState.Loading
            
            try {
                repository.observeInstructionsByUserId(userId).collect { instructions ->
                    _instructions.value = instructions
                    _instructionState.value = InstructionState.Success
                }
            } catch (e: Exception) {
                _instructionState.value = InstructionState.Error(e.message ?: "Failed to load instructions")
            }
        }
    }
    
    fun loadAllInstructions() {
        viewModelScope.launch {
            _instructionState.value = InstructionState.Loading
            
            try {
                repository.observeAllInstructions().collect { instructions ->
                    _instructions.value = instructions
                    _instructionState.value = InstructionState.Success
                }
            } catch (e: Exception) {
                _instructionState.value = InstructionState.Error(e.message ?: "Failed to load instructions")
            }
        }
    }
    
    fun createInstruction(title: String, description: String, userId: String) {
        viewModelScope.launch {
            _instructionState.value = InstructionState.Loading
            
            val instruction = Instruction(
                userId = userId,
                title = title,
                description = description
            )
            
            val result = repository.createInstruction(instruction)
            
            if (result.isSuccess) {
                // Refresh list so the user immediately sees their instruction
                try {
                    repository.observeInstructionsByUserId(userId).collect { instructions ->
                        _instructions.value = instructions
                        _instructionState.value = InstructionState.Success
                        return@collect
                    }
                } catch (e: Exception) {
                    _instructionState.value = InstructionState.Error(e.message ?: "Failed to refresh instructions")
                }
            } else {
                _instructionState.value = InstructionState.Error(result.exceptionOrNull()?.message ?: "Failed to create instruction")
            }
        }
    }
    
    fun updateInstruction(instruction: Instruction) {
        viewModelScope.launch {
            _instructionState.value = InstructionState.Loading
            
            val result = repository.updateInstruction(instruction)
            
            _instructionState.value = if (result.isSuccess) {
                InstructionState.Success
            } else {
                InstructionState.Error(result.exceptionOrNull()?.message ?: "Failed to update instruction")
            }
        }
    }
    
    fun getInstructionById(instructionId: String): Instruction? {
        return _instructions.value.find { it.instructionId == instructionId }
    }
    
    fun clearError() {
        if (_instructionState.value is InstructionState.Error) {
            _instructionState.value = InstructionState.Idle
        }
    }
}

sealed class InstructionState {
    object Idle : InstructionState()
    object Loading : InstructionState()
    object Success : InstructionState()
    data class Error(val message: String) : InstructionState()
}

