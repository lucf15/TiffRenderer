/*
 * Copyright 2026 lucf15
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#ifndef TIFFRENDERER_AFFINE_H
#define TIFFRENDERER_AFFINE_H

#include <cmath>
#include <limits>

namespace tiffrenderer {

// A 2D affine transform matching android.graphics.Matrix#getValues() layout; invert() is used to map destination pixels back to source pixels.
class AffineTransform {
public:
    AffineTransform() : AffineTransform(1, 0, 0, 0, 1, 0) {}

    AffineTransform(float mxx, float mxy, float mtx, float myx, float myy, float mty)
        : mxx_(mxx), mxy_(mxy), mtx_(mtx), myx_(myx), myy_(myy), mty_(mty) {}

    void apply(float x, float y, float* outX, float* outY) const {
        *outX = mxx_ * x + mxy_ * y + mtx_;
        *outY = myx_ * x + myy_ * y + mty_;
    }

    bool invert(AffineTransform* out) const {
        // std::fabs(NaN) < 1e-9f is false, so a non-finite component must be checked explicitly.
        if (!std::isfinite(mxx_) || !std::isfinite(mxy_) || !std::isfinite(mtx_)
                || !std::isfinite(myx_) || !std::isfinite(myy_) || !std::isfinite(mty_)) {
            return false;
        }
        const double det = static_cast<double>(mxx_) * myy_ - static_cast<double>(mxy_) * myx_;
        if (!std::isfinite(det) || det == 0) return false;
        const double inverse[6] = {
            myy_ / det, -mxy_ / det, -(static_cast<double>(myy_) * mtx_ - static_cast<double>(mxy_) * mty_) / det,
            -myx_ / det, mxx_ / det, -(-static_cast<double>(myx_) * mtx_ + static_cast<double>(mxx_) * mty_) / det
        };
        for (double value : inverse) {
            if (!std::isfinite(value) || std::fabs(value) > std::numeric_limits<float>::max()) return false;
        }
        *out = AffineTransform(static_cast<float>(inverse[0]), static_cast<float>(inverse[1]),
                               static_cast<float>(inverse[2]), static_cast<float>(inverse[3]),
                               static_cast<float>(inverse[4]), static_cast<float>(inverse[5]));
        return true;
    }

private:
    float mxx_, mxy_, mtx_, myx_, myy_, mty_;
};

}  // namespace tiffrenderer

#endif  // TIFFRENDERER_AFFINE_H
