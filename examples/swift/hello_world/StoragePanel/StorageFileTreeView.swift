// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import SwiftUI

struct StorageFileTreeView: View {
    private let childrenByParent: [String: [StorageLabStore.Entry]]
    let onDelete: (StorageLabStore.Entry) -> Void

    init(entries: [StorageLabStore.Entry], onDelete: @escaping (StorageLabStore.Entry) -> Void) {
        self.childrenByParent = Dictionary(grouping: entries) { entry in
            entry.path.split(separator: "/").dropLast().joined(separator: "/")
        }.mapValues { children in
            children.sorted { left, right in
                if left.isDirectory != right.isDirectory {
                    return left.isDirectory
                }
                return left.path.localizedStandardCompare(right.path) == .orderedAscending
            }
        }
        self.onDelete = onDelete
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Label(StorageLabStore.folderName, systemImage: "folder.fill")
                .font(.headline)
                .foregroundColor(Theme.textPrimary)
            Divider()
            ForEach(self.childrenByParent[""] ?? []) { entry in
                StorageFileTreeBranch(entry: entry, childrenByParent: self.childrenByParent,
                                      onDelete: self.onDelete)
            }
        }
        .accessibilityIdentifier("storage.files.tree")
    }
}

private struct StorageFileTreeBranch: View {
    let entry: StorageLabStore.Entry
    let childrenByParent: [String: [StorageLabStore.Entry]]
    let onDelete: (StorageLabStore.Entry) -> Void
    @State private var expanded = false

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            self.row
            if self.entry.isDirectory && self.expanded {
                VStack(alignment: .leading, spacing: 4) {
                    if (self.childrenByParent[self.entry.path] ?? []).isEmpty {
                        Text("Empty folder")
                            .font(.caption)
                            .foregroundColor(Theme.textSecondary)
                            .padding(.vertical, 6)
                    }
                    ForEach(self.childrenByParent[self.entry.path] ?? []) { child in
                        StorageFileTreeBranch(entry: child, childrenByParent: self.childrenByParent,
                                              onDelete: self.onDelete)
                    }
                }
                .padding(.leading, 24)
                .overlay(alignment: .leading) {
                    Rectangle()
                        .fill(Theme.border)
                        .frame(width: 1)
                        .padding(.leading, 6)
                }
            }
        }
    }

    private var row: some View {
        HStack(spacing: 8) {
            if self.entry.isDirectory {
                Button {
                    self.expanded.toggle()
                } label: {
                    self.rowLabel
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel(self.entry.path)
                .accessibilityValue(self.expanded ? "Expanded" : "Collapsed")
                .accessibilityHint("Double tap to expand or collapse folder")
            } else {
                self.rowLabel
            }
            Spacer(minLength: 0)
            Menu {
                Button {
                    UIPasteboard.general.string = StorageLabStore.folderName + "/" + self.entry.path
                } label: {
                    Label("Copy command path", systemImage: "doc.on.doc")
                }
                Button(role: .destructive) { self.onDelete(self.entry) } label: {
                    Label("Delete", systemImage: "trash")
                }
            } label: {
                Image(systemName: "ellipsis")
                    .frame(width: 44, height: 44)
                    .contentShape(Rectangle())
            }
            .accessibilityLabel("Actions for \(self.entry.path)")
        }
        .frame(minHeight: 44)
    }

    private var rowLabel: some View {
        HStack(spacing: 8) {
            Image(systemName: self.expanded ? "chevron.down" : "chevron.right")
                .font(.caption.weight(.semibold))
                .foregroundColor(Theme.textSecondary)
                .frame(width: 12)
                .opacity(self.entry.isDirectory ? 1 : 0)
            Image(systemName: self.entry.isDirectory
                  ? (self.expanded ? "folder.fill" : "folder") : "doc")
                .foregroundColor(self.entry.isDirectory ? Theme.primary : Theme.textSecondary)
                .frame(width: 20)
            VStack(alignment: .leading, spacing: 2) {
                Text(self.entry.path.split(separator: "/").last.map(String.init) ?? self.entry.path)
                    .font(.subheadline)
                    .foregroundColor(Theme.textPrimary)
                    .lineLimit(1)
                    .truncationMode(.middle)
                if !self.entry.isDirectory {
                    Text("\(self.entry.bytes) bytes · \(self.entry.bytes / 1024) KiB")
                        .font(.caption)
                        .foregroundColor(Theme.textSecondary)
                }
            }
        }
    }
}
