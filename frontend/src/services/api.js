import axios from 'axios';

const API_BASE_URL = process.env.REACT_APP_API_URL || 'http://localhost:8080/api';

/**
 * Detect document types (intents) from question.
 * @param {string} question - User's question
 * @returns {Promise<string[]>} - Array of document types (e.g., ["METADATA", "LINEAGE"])
 */
export const detectIntent = async (question) => {
  try {
    const response = await axios.post(`${API_BASE_URL}/rag/detect-intent`, { 
      question 
    });
    return response.data; // Returns array of doc_types
  } catch (error) {
    console.error('Error detecting intent:', error);
    throw error;
  }
};

/**
 * Search vector database for relevant documents.
 * @param {string} question - User's question
 * @param {string[]} docTypes - Array of document types to search
 * @param {Object} options - Optional parameters (table, keyspace, topK)
 * @returns {Promise<Object>} - Response with documents array and count
 */
export const searchVector = async (question, docTypes, options = {}) => {
  try {
    const response = await axios.post(`${API_BASE_URL}/rag/search-vector`, {
      question,
      docTypes,
      table: options.table,
      keyspace: options.keyspace,
      topK: options.topK
    });
    return response.data; // Returns { documents: [], count: number }
  } catch (error) {
    console.error('Error searching vectors:', error);
    throw error;
  }
};

/**
 * Full RAG query: Ask question and get answer.
 * This endpoint handles the complete flow: intent detection -> vector search -> LLM generation.
 * @param {string} question - User's question
 * @param {Object} options - Optional parameters
 * @returns {Promise<string>} - Generated answer from Phi-4
 */
export const askQuestion = async (question, options = {}) => {
  try {
    const response = await axios.post(`${API_BASE_URL}/rag/ask`, {
      question,
      table: options.table || 'dda_transactions',
      keyspace: options.keyspace || 'transaction_keyspace',
      topK: options.topK || 6,
      temperature: options.temperature || 0.3,
      maxTokens: options.maxTokens || 100
    }, {
      timeout: 600000 // 10 minutes for CPU-only inference
    });
    return response.data; // Returns answer string
  } catch (error) {
    console.error('Error asking question:', error);
    throw error;
  }
};

/**
 * Full RAG query with detailed step-by-step progress.
 * Returns answer plus detailed progress for each step.
 * @param {string} question - User's question
 * @param {Object} options - Optional parameters
 * @returns {Promise<Object>} - Response with answer, steps, and summary
 */
export const askQuestionDetailed = async (question, options = {}) => {
  try {
    const response = await axios.post(`${API_BASE_URL}/rag/ask-detailed`, {
      question,
      table: options.table || 'dda_transactions',
      keyspace: options.keyspace || 'transaction_keyspace',
      topK: options.topK || 6,
      temperature: options.temperature || 0.3,
      maxTokens: options.maxTokens || 100
    }, {
      timeout: 600000 // 10 minutes for CPU-only inference
    });
    return response.data; // Returns { answer, steps: [], summary: {} }
  } catch (error) {
    console.error('Error asking question with details:', error);
    throw error;
  }
};

/**
 * Legacy endpoint: /rag/query (for backward compatibility)
 */
export const queryRag = async (question, options = {}) => {
  try {
    const response = await axios.post(`${API_BASE_URL}/rag/query`, {
      question,
      keyspace: options.keyspace || 'transaction_keyspace',
      table: options.table || 'dda_transactions',
      timeRange: options.timeRange || '7d',
      topK: options.topK || 6,
      temperature: options.temperature || 0.3,
      maxTokens: options.maxTokens || 100
    }, {
      timeout: 600000
    });
    return response.data;
  } catch (error) {
    console.error('Error in RAG query:', error);
    throw error;
  }
};

