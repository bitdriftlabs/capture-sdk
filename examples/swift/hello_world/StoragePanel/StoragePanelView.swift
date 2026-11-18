// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import SwiftUI

struct StoragePanelView: View {
    var body: some View {
        PanelSection(title: "Storage", subtitle: "Prepare local data to test settings and cache commands.") {
            NavigationLink(destination: StorageLabView()) {
                PanelRow(title: "Storage lab", subtitle: "UserDefaults, files, and directories",
                         showsChevron: true)
            }
            .buttonStyle(PressableCardButtonStyle())
        }
    }
}

private struct StorageLabView: View {
    @Environment(\.scenePhase)
    private var scenePhase
    @State private var suite = StorageLabStore.testSuite
    @State private var key = "demo.message"
    @State private var value = "Hello from the demo"
    @State private var valueType = StorageLabStore.ValueType.string
    @State private var values: [String: Any] = [:]
    @State private var directory = StorageLabStore.Directory.caches
    @State private var path = "sample.txt"
    @State private var sizeKiB = "4"
    @State private var root: URL?
    @State private var entries: [StorageLabStore.Entry] = []
    @State private var pendingDeletion: StorageLabStore.Entry?
    @State private var status = ""
    @State private var failed = false
    private let store = StorageLabStore()

    var body: some View {
        PanelScreen {
            if !self.status.isEmpty {
                Text(self.status)
                    .font(.footnote)
                    .foregroundColor(self.failed ? Theme.warning : Theme.textSecondary)
                    .accessibilityIdentifier("storage.status")
            }
            self.defaultsEditor
            self.defaultsList
            self.fileEditor
            self.fileList
        }
        .navigationTitle("Storage Lab")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            Button("Refresh") { self.refresh() }
                .accessibilityIdentifier("storage.refresh")
        }
        .onAppear { self.refresh() }
        .onChange(of: self.suite) { _ in self.refresh() }
        .onChange(of: self.directory) { _ in self.refresh() }
        .onChange(of: self.scenePhase) { phase in
            if phase == .active { self.refresh() }
        }
        .confirmationDialog("Delete directory and all its contents?", isPresented: Binding(
            get: { self.pendingDeletion != nil },
            set: { if !$0 { self.pendingDeletion = nil } }
        ), titleVisibility: .visible) {
            if let entry = self.pendingDeletion {
                Button("Delete \(entry.path)", role: .destructive) { self.delete(entry) }
            }
        }
    }

    private var defaultsEditor: some View {
        PanelSection(title: "UserDefaults", subtitle: "Leave suite empty to use UserDefaults.standard.") {
            PanelCard {
                PanelInputField(title: "Suite name", placeholder: "Empty = standard", text: self.$suite)
                Text(self.suite.isEmpty ? "Remote arguments: omit suite_name" : "suite_name: \(self.suite)")
                    .font(.caption.monospaced())
                    .textSelection(.enabled)
                PanelInputField(title: "Key", placeholder: "demo.message", text: self.$key,
                                accessibilityID: "storage.defaults.key")
                Picker("Type", selection: self.$valueType) {
                    ForEach(StorageLabStore.ValueType.allCases) { type in
                        Text(type.rawValue).tag(type)
                    }
                }
                PanelInputField(title: "Value", placeholder: "Value", text: self.$value,
                                accessibilityID: "storage.defaults.value")
                Button("Set value") {
                    self.perform("Saved \(self.key).") {
                        try self.store.setDefault(key: self.key, text: self.value,
                                                  type: self.valueType, suite: self.suite)
                    }
                }
                .buttonStyle(FilledButtonStyle())
                Button("Add sample values") {
                    self.perform("Added demo.message, demo.enabled, demo.count, and demo.options.") {
                        let defaults = try self.store.defaults(suite: self.suite)
                        defaults.set("Hello from the demo", forKey: "demo.message")
                        defaults.set(true, forKey: "demo.enabled")
                        defaults.set(42, forKey: "demo.count")
                        let options: [String: Any] = ["theme": "dark", "tags": ["ios", "test"]]
                        defaults.set(options, forKey: "demo.options")
                    }
                }
                .buttonStyle(OutlineButtonStyle())
            }
        }
    }

    private var defaultsList: some View {
        PanelSection(title: "Persisted values (\(self.values.count))",
                     subtitle: "Refresh after remote get / set / remove / clear commands.") {
            if self.values.isEmpty {
                Text("No persisted values in this domain.")
                    .foregroundColor(Theme.textSecondary)
            }
            ForEach(self.values.keys.sorted(), id: \.self) { key in
                PanelCard {
                    Text(key).font(.headline).textSelection(.enabled)
                    if let value = self.values[key] {
                        Text("\(String(describing: type(of: value))) · \(String(describing: value))")
                            .font(.caption.monospaced())
                            .textSelection(.enabled)
                    }
                    HStack {
                        Button("Use key") { self.key = key }
                        Spacer()
                        Button("Remove", role: .destructive) {
                            self.perform("Removed \(key).") {
                                try self.store.defaults(suite: self.suite).removeObject(forKey: key)
                            }
                        }
                    }
                }
            }
        }
    }

    private var fileEditor: some View {
        PanelSection(title: "Files", subtitle: "Files are created inside a StorageLab subdirectory.") {
            PanelCard {
                Picker("OS directory", selection: self.$directory) {
                    ForEach(StorageLabStore.Directory.allCases) { directory in
                        Text(directory.rawValue).tag(directory)
                    }
                }
                if let root = self.root {
                    Text(root.path)
                        .font(.caption.monospaced())
                        .foregroundColor(Theme.textSecondary)
                        .textSelection(.enabled)
                }
                Text(self.directory == .caches
                        ? "Command path: StorageLab/sample.txt. Delete directories with recursive: true."
                        : "The merged file commands only access Caches. This directory is for comparison.")
                    .font(.footnote)
                    .foregroundColor(Theme.textSecondary)
                PanelInputField(title: "Relative path", placeholder: "nested/sample.txt", text: self.$path,
                                accessibilityID: "storage.files.path")
                PanelInputField(title: "File size (KiB, 1–10240)", placeholder: "4", text: self.$sizeKiB)
                    .keyboardType(.numberPad)
                Button("Create file") { self.create(directory: false) }
                    .buttonStyle(FilledButtonStyle())
                Button("Create directory") { self.create(directory: true) }
                    .buttonStyle(OutlineButtonStyle())
                Text("Files contain repeated A bytes. Nested paths create parent directories automatically.")
                    .font(.footnote)
                    .foregroundColor(Theme.textSecondary)
            }
        }
    }

    private var fileList: some View {
        let bytes = self.entries.reduce(0) { $0 + $1.bytes }
        return PanelSection(
            title: "Contents (\(self.entries.count))",
            subtitle: "\(bytes) bytes · \(bytes / 1024) KiB. Refresh after remote file commands."
        ) {
            if self.entries.isEmpty {
                Text("No lab files. The lab directory may be empty or deleted.")
                    .foregroundColor(Theme.textSecondary)
            }
            if !self.entries.isEmpty {
                PanelCard {
                    StorageFileTreeView(entries: self.entries) { entry in
                        if entry.isDirectory { self.pendingDeletion = entry } else { self.delete(entry) }
                    }
                    .id(self.directory)
                }
            }
        }
    }

    private func refresh() {
        do {
            self.values = self.store.values(suite: self.suite)
            let root = try self.store.root(for: self.directory)
            self.root = root
            self.entries = try self.store.entries(root: root)
        } catch {
            self.entries = []
            self.status = error.localizedDescription
            self.failed = true
        }
    }

    private func perform(_ message: String, action: () throws -> Void) {
        do {
            try action()
            self.status = message
            self.failed = false
        } catch {
            self.status = error.localizedDescription
            self.failed = true
        }
        self.refresh()
    }

    private func create(directory: Bool) {
        self.perform("Created \(self.path).") {
            guard let root = self.root else { throw StorageLabStore.LabError("Directory unavailable.") }
            guard let size = Int(self.sizeKiB) else { throw StorageLabStore.LabError("Enter a size in KiB.") }
            try self.store.create(root: root, path: self.path, sizeKiB: size, directory: directory)
        }
    }

    private func delete(_ entry: StorageLabStore.Entry) {
        self.perform("Deleted \(entry.path).") {
            guard let root = self.root else { throw StorageLabStore.LabError("Directory unavailable.") }
            try self.store.delete(root: root, path: entry.path)
        }
    }
}
