// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Foundation

final class APIClient {
    enum Error: Swift.Error {
        case `internal`(message: String?)
        case remote(error: Swift.Error?)
    }

    private lazy var delegateQueue = OperationQueue.serial(
        withLabelSuffix: "HTTPClient",
        target: .network
    )

    private lazy var session: URLSession = {
        let configuration = URLSessionConfiguration.ephemeral
        configuration.timeoutIntervalForRequest = 15.0
        configuration.timeoutIntervalForResource = 45.0
        return URLSession(configuration: configuration, delegate: nil, delegateQueue: self.delegateQueue)
    }()

    private let apiURL: URL
    private let apiKey: String

    private let encoder = JSONEncoder()
    private let decoder = JSONDecoder()

    init(apiURL: URL, apiKey: String) {
        self.apiURL = apiURL
        self.apiKey = apiKey
    }

    func perform<Response: Decodable>(endpoint: APIEndpoint, request: some Encodable,
                                      headers: [String: String]? = nil,
                                      completion: @escaping (Result<Response, Swift.Error>) -> Void)
    {
        do {
            let body = try self.encoder.encode(request)

            self.perform(path: endpoint.path, body: body, headers: headers) { [weak self] result in
                guard let self else {
                    return completion(.failure(Error.internal(message: nil)))
                }

                switch result {
                case let .success(data):
                    guard let data else {
                        return completion(.failure(
                            Error.internal(message: "server response without data")
                        ))
                    }

                    do {
                        let response = try self.decoder.decode(Response.self, from: data)
                        completion(.success(response))
                    } catch let error {
                        completion(.failure(
                            Error
                                .internal(
                                    message: "failed to decode the response from the server: \(error)"
                                )
                        ))
                    }
                case let .failure(error):
                    completion(.failure(error))
                }
            }
        } catch let error {
            return completion(.failure(Error.internal(
                message: "failed to encode request: \(error)"
            )))
        }
    }

    func perform(endpoint: APIEndpoint, request: some Encodable, headers: [String: String]? = nil,
                 completion: @escaping (Result<Void, Swift.Error>) -> Void)
    {
        do {
            let body = try self.encoder.encode(request)

            self.perform(path: endpoint.path, body: body, headers: headers) { result in
                switch result {
                case .success:
                    completion(.success(()))
                case let .failure(error):
                    completion(.failure(error))
                }
            }
        } catch let error {
            return completion(.failure(Error.internal(
                message: "failed to encode request: \(error)"
            )))
        }
    }

    // MARK: - Private

    private func perform(path: String, body: Data?, headers: [String: String]? = nil,
                         completion: @escaping (Result<Data?, Swift.Error>) -> Void)
    {
        let url = self.apiURL.appendingPathComponent(path)

        var urlRequest = URLRequest(url: url, cachePolicy: .reloadIgnoringLocalCacheData)
        urlRequest.setInternalHeaders()
        urlRequest.httpMethod = "POST"
        urlRequest.httpBody = body

        if let headers {
            for (headerName, headerValue) in headers {
                urlRequest.setValue(headerValue, forHTTPHeaderField: headerName)
            }
        }

        urlRequest.setValue("application/json", forHTTPHeaderField: "Content-Type")
        urlRequest.setValue(self.apiKey, forHTTPHeaderField: "x-bitdrift-api-key")

        self.session.dataTask(with: urlRequest) { data, response, error in
            let status = HTTPStatus(response: response, error: error)
            guard status.isSuccess else {
                return completion(.failure(Error.remote(error: error)))
            }

            completion(.success(data))
        }.resume()
    }
}
