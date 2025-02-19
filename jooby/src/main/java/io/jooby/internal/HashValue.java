/*
 * Jooby https://jooby.io
 * Apache License Version 2.0 https://jooby.io/LICENSE.txt
 * Copyright 2014 Edgar Espina
 */
package io.jooby.internal;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;
import java.util.TreeMap;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.jooby.Context;
import io.jooby.FileUpload;
import io.jooby.ValueNode;

public class HashValue implements ValueNode {
  private static final Map<String, ValueNode> EMPTY = Collections.emptyMap();

  private Context ctx;

  private Map<String, ValueNode> hash = EMPTY;

  private final String name;

  public HashValue(Context ctx, String name, Supplier<Map<String, ValueNode>> mapSupplier) {
    this.ctx = ctx;
    this.name = name;
    this.hash = mapSupplier.get();
  }

  public HashValue(Context ctx, String name) {
    this.ctx = ctx;
    this.name = name;
  }

  protected HashValue(Context ctx) {
    this.ctx = ctx;
    this.name = null;
  }

  @Override
  public String name() {
    return name;
  }

  public void put(String path, String value) {
    put(path, Collections.singletonList(value));
  }

  public void put(String path, ValueNode node) {
    put(
        path,
        (name, scope) -> {
          ValueNode existing = scope.get(name);
          if (existing == null) {
            scope.put(name, node);
          } else {
            ArrayValue list;
            if (existing instanceof ArrayValue) {
              list = (ArrayValue) existing;
            } else {
              list = new ArrayValue(ctx, name).add(existing);
              scope.put(name, list);
            }
            list.add(node);
          }
        });
  }

  public void put(String path, Collection<String> values) {
    put(
        path,
        (name, scope) -> {
          for (String value : values) {
            ValueNode existing = scope.get(name);
            if (existing == null) {
              scope.put(name, new SingleValue(ctx, name, decode(value)));
            } else {
              ArrayValue list;
              if (existing instanceof ArrayValue) {
                list = (ArrayValue) existing;
              } else {
                list = new ArrayValue(ctx, name).add(existing);
                scope.put(name, list);
              }
              list.add(decode(value));
            }
          }
        });
  }

  protected String decode(String value) {
    return value;
  }

  private void put(String path, BiConsumer<String, Map<String, ValueNode>> consumer) {
    // Locate node:
    int nameStart = 0;
    int nameEnd = path.length();
    HashValue target = this;
    for (int i = nameStart; i < nameEnd; i++) {
      char ch = path.charAt(i);
      if (ch == '.') {
        String name = path.substring(nameStart, i);
        nameStart = i + 1;
        target = target.getOrCreateScope(name);
      } else if (ch == '[') {
        if (nameStart < i) {
          String name = path.substring(nameStart, i);
          target = target.getOrCreateScope(name);
        }
        nameStart = i + 1;
      } else if (ch == ']') {
        if (i + 1 < nameEnd) {
          String name = path.substring(nameStart, i);
          if (isNumber(name)) {
            target.useIndexes();
          }
          nameStart = i + 1;
          target = target.getOrCreateScope(name);
        } else {
          nameEnd = i;
        }
      }
    }
    String key = path.substring(nameStart, nameEnd);
    if (isNumber(key)) {
      target.useIndexes();
    }
    // Final node
    consumer.accept(key, target.hash());
  }

  private void useIndexes() {
    if (hash instanceof TreeMap) {
      return;
    }
    TreeMap<String, ValueNode> ordered = new TreeMap<>();
    ordered.putAll(hash);
    hash.clear();
    this.hash = ordered;
  }

  private boolean isNumber(String value) {
    for (int i = 0; i < value.length(); i++) {
      if (!Character.isDigit(value.charAt(i))) {
        return false;
      }
    }
    return true;
  }

  private Map<String, ValueNode> hash() {
    if (hash == EMPTY) {
      hash = new LinkedHashMap<>();
    }
    return hash;
  }

  /*package*/ HashValue getOrCreateScope(String name) {
    return (HashValue) hash().computeIfAbsent(name, k -> new HashValue(ctx, k));
  }

  public ValueNode get(@NonNull String name) {
    ValueNode value = hash.get(name);
    if (value == null) {
      return new MissingValue(scope(name));
    }
    return value;
  }

  private String scope(String name) {
    return this.name == null ? name : this.name + "." + name;
  }

  @Override
  public ValueNode get(@NonNull int index) {
    return get(Integer.toString(index));
  }

  public int size() {
    return hash.size();
  }

  @Override
  public String value() {
    StringJoiner joiner = new StringJoiner("&");
    hash.forEach(
        (k, v) -> {
          Iterator<ValueNode> it = v.iterator();
          while (it.hasNext()) {
            ValueNode value = it.next();
            String str =
                value instanceof FileUpload ? ((FileUpload) value).getFileName() : value.toString();
            joiner.add(k + "=" + str);
          }
        });
    return joiner.toString();
  }

  @Override
  public Iterator<ValueNode> iterator() {
    return hash.values().iterator();
  }

  @NonNull @Override
  public List<String> toList() {
    return toList(String.class);
  }

  @NonNull @Override
  public Set<String> toSet() {
    return toSet(String.class);
  }

  @NonNull @Override
  public <T> List<T> toList(@NonNull Class<T> type) {
    return toCollection(type, new ArrayList<>());
  }

  @NonNull @Override
  public <T> Set<T> toSet(@NonNull Class<T> type) {
    return toCollection(type, new LinkedHashSet<>());
  }

  @NonNull @Override
  public <T> Optional<T> toOptional(@NonNull Class<T> type) {
    if (hash.isEmpty()) {
      return Optional.empty();
    }
    return Optional.ofNullable(to(type));
  }

  @NonNull @Override
  public <T> T to(@NonNull Class<T> type) {
    return ctx.convert(this, type);
  }

  @Override
  public Map<String, List<String>> toMultimap() {
    Map<String, List<String>> result = new LinkedHashMap<>(hash.size());
    Set<Map.Entry<String, ValueNode>> entries = hash.entrySet();
    String scope = name == null ? "" : name + ".";
    for (Map.Entry<String, ValueNode> entry : entries) {
      ValueNode value = entry.getValue();
      value
          .toMultimap()
          .forEach(
              (k, v) -> {
                result.put(scope + k, v);
              });
    }
    return result;
  }

  @Override
  public String toString() {
    return hash.toString();
  }

  public void put(Map<String, Collection<String>> headers) {
    for (Map.Entry<String, Collection<String>> entry : headers.entrySet()) {
      put(entry.getKey(), entry.getValue());
    }
  }

  private <T, C extends Collection<T>> C toCollection(@NonNull Class<T> type, C collection) {
    if (hash instanceof TreeMap) {
      // indexes access, treat like a list
      Collection<ValueNode> values = hash.values();
      for (ValueNode value : values) {
        collection.add(value.to(type));
      }
    } else {
      collection.add(to(type));
    }
    return collection;
  }
}
