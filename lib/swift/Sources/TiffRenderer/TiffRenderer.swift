import Foundation
import UIKit
import TiffRendererCore

public enum TiffRenderer {
    public enum InputError: Error { case invalidPageOrSize }

    @MainActor
    public static func render(
        url: URL,
        pageIndex: Int = 0,
        maxWidth: Int,
        maxHeight: Int = Int(Int32.max)
    ) async throws -> UIImage {
        try Task.checkCancellation()
        guard let page = Int32(exactly: pageIndex), page >= 0,
              let width = Int32(exactly: maxWidth), width > 0,
              let height = Int32(exactly: maxHeight), height > 0 else {
            throw InputError.invalidPageOrSize
        }
        let request = RequestBox(AppleTiffRenderer.companion.request(
            url: url, pageIndex: page, maxWidth: width, maxHeight: height
        ))
        return try await withTaskCancellationHandler {
            do {
                let image = try await request.value.awaitImage()
                try Task.checkCancellation()
                return image
            } catch {
                if Task.isCancelled { throw CancellationError() }
                throw error
            }
        } onCancel: {
            request.value.cancel()
        }
    }
}

private final class RequestBox: @unchecked Sendable {
    let value: AppleTiffRenderRequest
    init(_ value: AppleTiffRenderRequest) { self.value = value }
}
