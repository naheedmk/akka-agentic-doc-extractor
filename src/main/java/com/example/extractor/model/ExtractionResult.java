package com.example.extractor.model;

import java.util.List;
import java.util.Map;

public class ExtractionResult {
  public String document_type;
  public Map<String, Object> fields;
  public List<Map<String, Object>> line_items;
}

